package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.domain.Series;
import zelytra.librarius.domain.Work;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class SeriesRepository implements PanacheRepositoryBase<Series, UUID> {

    /**
     * Looks a series up by its title within a kind, case-insensitively — the same key as the
     * {@code uq_series_kind_title} unique index, so that importing volume 12 of a run
     * attaches it to the series volume 1 already created.
     */
    public Optional<Series> findByKindAndTitle(Kind kind, String title) {
        if (kind == null || title == null || title.isBlank()) {
            return Optional.empty();
        }
        return find("kind = ?1 and lower(title) = ?2", kind, title.trim().toLowerCase(Locale.ROOT))
                .firstResultOptional();
    }

    /**
     * How many volumes of a series the user owns, and how many of those they have read.
     *
     * @param seriesId the series the counters belong to
     * @param owned    distinct works of the series present in the user's collection
     * @param read     those of them marked {@link LibraryStatus#READ}
     */
    /** @param lastActivity the latest activity among the volumes held, see {@code LibraryItemRepository.ACTIVITY} */
    public record OwnershipCounters(UUID seriesId, long owned, long read, java.time.LocalDate lastActivity) {
    }

    /**
     * Ownership counters for every series the user owns at least one volume of, in a single
     * grouped query — the series list must not cost one query per series.
     *
     * <p>Counting distinct works rather than items keeps two editions of the same volume
     * from inflating the run.
     */
    public List<OwnershipCounters> ownershipCountersByUser(String userId) {
        return getEntityManager()
                .createQuery("""
                        select w.series.id, count(distinct w.id),
                               count(distinct case when li.status = :read then w.id end),
                               max(%s)
                        from LibraryItem li
                          join li.edition e
                          join e.work w
                          left join li.progress p
                        where li.userId = :userId
                          and w.series is not null
                        group by w.series.id
                        """.formatted(LibraryItemRepository.ACTIVITY), Object[].class)
                .setParameter("userId", userId)
                .setParameter("read", LibraryStatus.READ)
                .getResultList()
                .stream()
                .map(row -> new OwnershipCounters((UUID) row[0], ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue(), (java.time.LocalDate) row[3]))
                .toList();
    }

    /** Loads several series at once, ordered by title so the list is stable. */
    public List<Series> listByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return list("id in ?1 order by lower(title) asc, id asc", ids);
    }

    /**
     * Every work attached to the series, in volume order. This is catalog data, shared by
     * all users: it is what tells the series screen that volume 4 exists at all, even when
     * nobody owns it yet.
     */
    public List<Work> worksOfSeries(UUID seriesId) {
        return getEntityManager()
                .createQuery("""
                        select w from Work w
                        where w.series.id = :seriesId
                        order by w.volumeNumber asc nulls last, lower(w.title) asc, w.id asc
                        """, Work.class)
                .setParameter("seriesId", seriesId)
                .getResultList();
    }

    /** Series still missing a volume total, capped so one refresh run stays bounded. */
    public List<Series> needingVolumeCount(int limit) {
        return needingVolumeCount(null, limit);
    }

    /**
     * Series still missing a total and not asked lately, the never-asked first (#429), so a
     * series no provider knows cannot hold the others back. Restricted to the series one user
     * holds a volume of when {@code userId} is given.
     */
    public List<Series> needingVolumeCount(String userId, int limit) {
        String held = userId == null ? "" : """
                 and exists (select 1 from LibraryItem li join li.edition e join e.work w
                             where w.series = s and li.userId = :userId)""";
        var query = getEntityManager().createQuery("""
                select s from Series s
                where s.totalVolumes is null
                  and (s.volumesCheckedAt is null or s.volumesCheckedAt < :before)%s
                order by s.volumesCheckedAt asc nulls first, s.id asc
                """.formatted(held), Series.class)
                .setParameter("before", java.time.OffsetDateTime.now().minusDays(VOLUME_RETRY_DAYS))
                .setMaxResults(limit);
        if (userId != null) {
            query.setParameter("userId", userId);
        }
        return query.getResultList();
    }

    /** Days before a series whose total no provider gave is asked again. */
    public static final int VOLUME_RETRY_DAYS = 7;

    /**
     * The cover of the lowest-numbered volume of each series that has one in the catalog,
     * whatever its owner (#433) — what a series with no cover of its own is shown with.
     */
    public java.util.Map<UUID, String> firstVolumeCovers(java.util.Collection<UUID> seriesIds) {
        if (seriesIds.isEmpty()) {
            return java.util.Map.of();
        }
        List<Object[]> rows = getEntityManager()
                .createQuery("""
                        select w.series.id, e.coverUrl
                        from Edition e
                          join e.work w
                        where w.series.id in :ids
                          and e.coverUrl is not null
                        order by w.volumeNumber asc nulls last, e.createdAt asc
                        """, Object[].class)
                .setParameter("ids", seriesIds)
                .getResultList();
        java.util.Map<UUID, String> covers = new java.util.LinkedHashMap<>();
        for (Object[] row : rows) {
            covers.putIfAbsent((UUID) row[0], (String) row[1]);
        }
        return covers;
    }
}
