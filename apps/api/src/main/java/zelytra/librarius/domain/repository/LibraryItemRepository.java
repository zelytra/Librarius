package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.genre.GenreNormalizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class LibraryItemRepository implements PanacheRepositoryBase<LibraryItem, UUID> {

    public List<LibraryItem> listByUser(String userId) {
        return list("userId = ?1 order by createdAt desc", userId);
    }

    /** Scoping-safe lookup: returns the item only if it belongs to the user. */
    public Optional<LibraryItem> findOwned(String userId, UUID id) {
        return find("id = ?1 and userId = ?2", id, userId).firstResultOptional();
    }

    public boolean deleteOwned(String userId, UUID id) {
        return delete("id = ?1 and userId = ?2", id, userId) > 0;
    }

    /**
     * The user's items belonging to a given series, edition and work fetched along the way:
     * the series screen needs the volume number and the title of every one of them.
     */
    public List<LibraryItem> listBySeries(String userId, UUID seriesId) {
        return getEntityManager()
                .createQuery("""
                        select li from LibraryItem li
                          join fetch li.edition e
                          join fetch e.work w
                          left join fetch li.progress
                        where li.userId = :userId
                          and w.series.id = :seriesId
                        order by w.volumeNumber asc nulls last, li.id asc
                        """, LibraryItem.class)
                .setParameter("userId", userId)
                .setParameter("seriesId", seriesId)
                .getResultList();
    }

    // ── Paged browsing ────────────────────────────────────────────────────────
    // The collection is filtered, sorted and sliced by the database. A 5000-title
    // library must cost the same to display as a 50-title one, and the front end
    // must never have to download the whole thing to show a shelf.

    /**
     * Everything that narrows the collection down, all fields optional but the user.
     *
     * @param userId    owner of the items, the only mandatory criterion
     * @param kind      restrict to books or to mangas
     * @param status    restrict to a reading status
     * @param rank      code of the rank category the item is filed under
     * @param genre     code of a genre the work carries, as {@code /api/genres} returns it
     * @param minRating keep only the titles rated at least that much — the "my favourites"
     *                  shelf is this filter at 4. Unrated titles never match
     * @param search    free text matched against the title, the authors and the series
     */
    public record LibraryFilter(String userId, Kind kind, LibraryStatus status, String rank,
            String genre, Integer minRating, String search) {
    }

    /** Orderings offered on the collection, exposed as the {@code sort} query parameter. */
    public enum LibrarySort {
        /** Most recently added first — the default. */
        ADDED("li.createdAt desc, li.id desc"),
        TITLE("lower(w.title) asc, li.id asc"),
        AUTHOR("lower(coalesce(w.authors, '')) asc, lower(w.title) asc, li.id asc"),
        // Still the free-text value: a work now carries several genres, so there is no such
        // thing as "its" genre to order on. The shelf keeps the ordering it had.
        GENRE("lower(coalesce(w.genresText, '')) asc, lower(w.title) asc, li.id asc"),
        /**
         * Best rated first. Unrated titles come last rather than first: they are the ones
         * the user has not judged, not the ones they judged worst.
         */
        RATING("li.rating desc nulls last, lower(w.title) asc, li.id asc");

        private final String clause;

        LibrarySort(String clause) {
            this.clause = clause;
        }

        /**
         * Resolves the lower-case value sent by the client. Every ordering ends on the
         * identifier so that two rows sharing a title never swap places between pages.
         *
         * @return the matching ordering, or empty when the value is not one of ours
         */
        public static Optional<LibrarySort> parse(String value) {
            if (value == null || value.isBlank()) {
                return Optional.of(ADDED);
            }
            return Arrays.stream(values())
                    .filter(s -> s.name().equalsIgnoreCase(value.trim()))
                    .findFirst();
        }
    }

    /** Number of items matching the filter, for the {@code total} of the envelope. */
    public long countMatching(LibraryFilter filter) {
        Map<String, Object> params = new LinkedHashMap<>();
        String where = whereClause(filter, params);
        TypedQuery<Long> query = getEntityManager().createQuery(
                "select count(li) from LibraryItem li join li.edition e join e.work w"
                        + " left join li.rankCategory rc where " + where,
                Long.class);
        params.forEach(query::setParameter);
        return query.getSingleResult();
    }

    /**
     * One page of the filtered collection, edition and work fetched along the way so that
     * rendering a page costs a single query whatever its size.
     */
    public List<LibraryItem> listMatching(LibraryFilter filter, LibrarySort sort, int offset,
            int limit) {
        Map<String, Object> params = new LinkedHashMap<>();
        String where = whereClause(filter, params);
        TypedQuery<LibraryItem> query = getEntityManager().createQuery(
                "select li from LibraryItem li join fetch li.edition e join fetch e.work w"
                        + " left join fetch li.rankCategory rc"
                        // Fetched, not lazily read one row at a time: every card on the
                        // home carousel shows where the reader is, so a page of the
                        // collection would otherwise cost one extra select per title.
                        + " left join fetch li.progress p where " + where
                        + " order by " + sort.clause,
                LibraryItem.class);
        params.forEach(query::setParameter);
        return query.setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    /**
     * Builds the shared {@code where} of the count and of the page, collecting the bound
     * parameters on the way so the two queries always agree on the criteria.
     */
    private static String whereClause(LibraryFilter filter, Map<String, Object> params) {
        List<String> clauses = new ArrayList<>();
        clauses.add("li.userId = :userId");
        params.put("userId", filter.userId());

        if (filter.kind() != null) {
            clauses.add("w.kind = :kind");
            params.put("kind", filter.kind());
        }
        if (filter.status() != null) {
            clauses.add("li.status = :status");
            params.put("status", filter.status());
        }
        if (filter.rank() != null && !filter.rank().isBlank()) {
            clauses.add("rc.code = :rank");
            params.put("rank", filter.rank().trim());
        }
        if (filter.genre() != null && !filter.genre().isBlank()) {
            // A subquery rather than a join: joining the genres would repeat an item once
            // per genre it carries, and a page would then hold fewer items than its size.
            clauses.add("exists (select 1 from Work gw join gw.genres g"
                    + " where gw.id = w.id and g.code = :genre)");
            // The client normally passes a code back; folding whatever it sent makes
            // `genre=Science Fiction` behave like `genre=science-fiction` rather than
            // silently matching nothing. A wording that folds to nothing stays unmatchable.
            String code = GenreNormalizer.code(filter.genre());
            params.put("genre", code != null ? code : filter.genre().trim());
        }
        if (filter.minRating() != null) {
            clauses.add("li.rating >= :minRating");
            params.put("minRating", filter.minRating());
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            clauses.add("""
                    (lower(w.title) like :search escape '!'
                     or lower(coalesce(w.authors, '')) like :search escape '!'
                     or lower(coalesce(w.seriesTitle, '')) like :search escape '!')""");
            params.put("search", likePattern(filter.search()));
        }
        return String.join(" and ", clauses);
    }

    /**
     * Turns a search term into a {@code like} pattern. The wildcards of the user are
     * escaped: typing {@code 100%} must look for that string, not for anything.
     */
    static String likePattern(String search) {
        String escaped = search.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    // ── Statistics ────────────────────────────────────────────────────────────
    // Aggregated in SQL rather than by walking the collection in memory: the cost
    // must not grow with the number of titles, /api/stats is hit on every render.

    /**
     * Per-status item counters, plus the pages of the read ones.
     *
     * @param read      items marked {@code READ}
     * @param reading   items marked {@code READING}
     * @param toRead    items marked {@code OWNED}, i.e. owned but not started
     * @param pagesRead total pages of the read items, ignoring editions with no page count
     */
    public record StatusTotals(long read, long reading, long toRead, long pagesRead) {
    }

    /**
     * A genre and the number of the user's items carrying it.
     *
     * @param code  identity of the genre, what the collection filter takes
     * @param label what a screen shows
     */
    public record GenreTotal(String code, String label, long count) {
    }

    /**
     * Counts the user's items per status and sums the pages of the read ones, in a single
     * grouped query. Editions carrying no page count contribute nothing, {@code SUM}
     * ignoring NULL.
     */
    public StatusTotals statusTotals(String userId) {
        List<Object[]> rows = getEntityManager()
                .createQuery("""
                        select li.status, count(li), sum(li.edition.pageCount)
                        from LibraryItem li
                        where li.userId = :userId
                        group by li.status
                        """, Object[].class)
                .setParameter("userId", userId)
                .getResultList();

        long read = 0;
        long reading = 0;
        long toRead = 0;
        long pagesRead = 0;
        for (Object[] row : rows) {
            long count = ((Number) row[1]).longValue();
            switch ((LibraryStatus) row[0]) {
                case READ -> {
                    read = count;
                    pagesRead = row[2] == null ? 0L : ((Number) row[2]).longValue();
                }
                case READING -> reading = count;
                case OWNED -> toRead = count;
            }
        }
        return new StatusTotals(read, reading, toRead, pagesRead);
    }

    /**
     * Number of distinct series in the user's collection.
     *
     * <p>A series is still identified by its lower-cased title: there is no {@code series}
     * table yet, so {@code work.series_title} is the only key available. Titles that carry
     * none — standalone works — are left out.
     */
    public long countDistinctSeries(String userId) {
        return getEntityManager()
                .createQuery("""
                        select count(distinct lower(li.edition.work.seriesTitle))
                        from LibraryItem li
                        where li.userId = :userId
                          and li.edition.work.seriesTitle is not null
                          and length(trim(li.edition.work.seriesTitle)) > 0
                        """, Long.class)
                .setParameter("userId", userId)
                .getSingleResult();
    }

    /**
     * The {@code limit} most represented genres in the user's collection, most frequent
     * first. Equal counts are broken alphabetically so the ranking stays stable from one
     * call to the next.
     *
     * <p>Grouped on the normalised genres, not on the free-text value: a title tagged
     * "Fantasy, Aventure" counts towards both, where it used to form a third genre of its
     * own. An item is counted once per genre it carries, so the counts add up to more than
     * the size of the collection — that is what a breakdown by genre means.
     */
    public List<GenreTotal> topGenres(String userId, int limit) {
        return getEntityManager()
                .createQuery("""
                        select g.code, g.label, count(li)
                        from LibraryItem li
                          join li.edition e
                          join e.work w
                          join w.genres g
                        where li.userId = :userId
                        group by g.code, g.label
                        order by count(li) desc, g.label asc, g.code asc
                        """, Object[].class)
                .setParameter("userId", userId)
                .setMaxResults(limit)
                .getResultList()
                .stream()
                .map(row -> new GenreTotal((String) row[0], (String) row[1],
                        ((Number) row[2]).longValue()))
                .toList();
    }
}
