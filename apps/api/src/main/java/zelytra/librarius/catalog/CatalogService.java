package zelytra.librarius.catalog;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import zelytra.librarius.domain.Kind;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

/**
 * Aggregates the catalog providers: for a set of kinds (or every registered one when
 * none is named), queries every matching provider (Open Library and the BnF for books,
 * AniList for mangas), then merges and deduplicates the results — a free-text search is
 * ranked by {@link CatalogRanker}. Results are cached to spare the external APIs.
 *
 * <p>The cache sits on the provider call rather than on the merged answer: the expensive
 * part is the outbound request, the merge is a walk over a few dozen records, and a
 * per-provider entry is what lets one provider's answer survive while another's expires.
 */
@ApplicationScoped
public class CatalogService {

    private final Map<Kind, List<CatalogProvider>> byKind = new EnumMap<>(Kind.class);

    private final Map<String, CatalogProvider> byName = new HashMap<>();

    private final CatalogCache cache;

    /**
     * Whether a coverless result with an ISBN is pointed at Open Library's cover for it. On by
     * default; off in the end-to-end stack, which serves fixed catalog answers and must not
     * depend on the internet for what its tiles show.
     */
    @org.eclipse.microprofile.config.inject.ConfigProperty(
            name = "librarius.catalog.isbn-covers", defaultValue = "true")
    boolean isbnCovers = true;

    @Inject
    public CatalogService(Instance<CatalogProvider> providers, CatalogCache cache) {
        this(providers.stream().toList(), cache);
    }

    /** Test-friendly constructor (without CDI). */
    CatalogService(List<CatalogProvider> providers, CatalogCache cache) {
        this.cache = cache;
        for (CatalogProvider provider : providers) {
            byKind.computeIfAbsent(provider.kind(), k -> new ArrayList<>()).add(provider);
            byName.putIfAbsent(provider.name(), provider);
        }
    }

    /**
     * Searches the requested kinds and merges their answers. An empty (or {@code null}) set
     * means every registered kind: one call then reaches every provider the instance knows,
     * across every medium. The per-provider cache key is built from {@code provider.kind()},
     * so a provider's entry is the same whether it was reached through a kind-scoped search or
     * a cross-medium one — nothing about the cache's shape changes.
     */
    public List<CatalogResult> search(Set<Kind> kinds, CatalogQuery query, int limit) {
        List<CatalogProvider> providers = providersFor(kinds);
        if (query.text() == null) {
            // An ISBN, an author, a year: nothing to rank on, so the providers' own order stands
            // (the round-robin), the most complete record first among equals.
            return aggregate(providers, limit, searchCall(query, limit), result -> 0);
        }
        List<CatalogResult> pool = roundRobin(callAll(providers, searchCall(query, limit)));
        CatalogRanker.Ranking ranking = CatalogRanker.rank(query.text(), pool, false);
        if (ranking.weak() && CatalogRanker.prefixRetryApplies(query)) {
            // Nothing matched the title convincingly and the last word may still be being
            // typed ("le nom du ven"): one more round, that word as a prefix, from the
            // providers that can truncate. Cached like any other call, and charged nothing:
            // the quota counts the reader's searches, not the provider calls one costs.
            CatalogQuery prefixed = query.withPrefixLastWord();
            List<CatalogProvider> truncating = providers.stream()
                    .filter(CatalogProvider::searchesByPrefix).toList();
            List<CatalogResult> widened = new ArrayList<>(pool);
            widened.addAll(roundRobin(callAll(truncating, searchCall(prefixed, limit))));
            ranking = CatalogRanker.rank(query.text(), widened, true);
        }
        return ranking.results().stream()
                .limit(limit)
                .map(r -> isbnCovers ? withIsbnCover(r) : r)
                .toList();
    }

    /** A provider's search, through the cache: the key names the kind, every criterion and the page. */
    private Function<CatalogProvider, List<CatalogResult>> searchCall(CatalogQuery query, int limit) {
        return provider -> cache.get(CatalogCache.Scope.SEARCH, provider.name(),
                "search|" + provider.kind() + '|' + query.cacheKey() + '|' + limit,
                () -> provider.search(query, limit));
    }

    public List<CatalogResult> upcoming(Kind kind, int limit) {
        return aggregate(byKind.getOrDefault(kind, List.of()), limit,
                provider -> cache.get(CatalogCache.Scope.UPCOMING,
                        provider.name(), "upcoming|" + kind + '|' + limit,
                        () -> provider.upcoming(limit)));
    }

    /**
     * The providers to query for a search: those registered for any of the named kinds, or —
     * when no kind is named — every registered provider across every medium. Iterated in the
     * {@link Kind} enum order so a cross-medium merge is deterministic.
     */
    private List<CatalogProvider> providersFor(Set<Kind> kinds) {
        List<CatalogProvider> selected = new ArrayList<>();
        for (Map.Entry<Kind, List<CatalogProvider>> entry : byKind.entrySet()) {
            if (kinds == null || kinds.isEmpty() || kinds.contains(entry.getKey())) {
                selected.addAll(entry.getValue());
            }
        }
        return selected;
    }

    /**
     * The other editions a provider knows of one of its works. Routed by name — the pair
     * {@code (provider, ref)} names a single record in a single catalogue — and cached like
     * every other outbound call.
     *
     * @param provider the catalogue the work was stored from ({@code work.provider})
     * @param ref      its reference for the work ({@code work.provider_ref})
     * @return the editions, or an empty list when the provider is unknown, holds no reference
     *         to key on, or simply knows no others
     */
    public List<CatalogResult> editionsOf(String provider, String ref, int limit) {
        CatalogProvider target = byName.get(provider);
        if (target == null || ref == null || ref.isBlank()) {
            return List.of();
        }
        return cache.get(CatalogCache.Scope.EDITIONS, target.name(),
                "editions|" + ref + '|' + limit,
                () -> target.editionsOf(ref, limit));
    }

    /**
     * The number of volumes a series is expected to run to, from whichever provider covers the
     * kind and knows — AniList for a manga; a book catalogue reports none. Off the request path
     * (the series-volume refresher), so it is not cached: a total is fetched once a day and
     * written to {@code series.total_volumes}, not read on every render.
     */
    public java.util.OptionalInt seriesVolumes(Kind kind, String title) {
        for (CatalogProvider provider : providersFor(kind == null ? Set.of() : Set.of(kind))) {
            java.util.OptionalInt volumes = provider.seriesVolumes(title);
            if (volumes.isPresent()) {
                return volumes;
            }
        }
        return java.util.OptionalInt.empty();
    }

    /**
     * Every work the providers credit to an author, by name — AniList lists a manga author's
     * works — merged and deduplicated like a search, and cached at the search level (keyed apart)
     * so repeat views of one author page cost nothing.
     */
    public List<CatalogResult> worksOfAuthor(Set<Kind> kinds, String name, int limit) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        String key = name.toLowerCase(Locale.ROOT).trim();
        Function<CatalogProvider, List<CatalogResult>> call = provider -> cache.get(
                CatalogCache.Scope.SEARCH, provider.name(), "authorworks|" + key + '|' + limit,
                () -> provider.worksOfAuthor(name, limit));
        // Every medium, whatever the kinds owned: an imported manga is filed as a book, and its
        // author's series would otherwise never be asked of AniList.
        List<CatalogProvider> all = providersFor(Set.of());
        List<CatalogResult> works = aggregate(
                all.stream().filter(CatalogProvider::listsWorks).toList(), limit, call);
        if (works.size() >= FEW_WORKS) {
            return works;
        }
        // An author the work-listing catalogues barely know — published in France only, say —
        // is filled in from the record catalogues, after what the others did find.
        List<CatalogResult> filled = new ArrayList<>(works);
        filled.addAll(aggregate(all.stream().filter(p -> !p.listsWorks()).toList(), limit, call));
        return filled.stream().limit(limit).toList();
    }

    /** Below this many works, an author's bibliography is filled in from record catalogues. */
    private static final int FEW_WORKS = 5;

    /**
     * Merges every provider's results round-robin, one rank at a time, rather than
     * exhausting the first provider before touching the next: with a shared limit that
     * used to let whichever provider answered first (or simply returned more) fill the
     * whole page, crowding out a catalogue that may hold the only copy of a title.
     *
     * <p>A provider that returns fewer results (including none, e.g. down or off-topic)
     * simply drops out of later rounds, and the limit it leaves unused goes to the
     * others — nobody is penalized for another provider's silence. Entries are folded on
     * {@link #aggregationKey} as they are merged, so every edition of one series+volume —
     * and any title two catalogues both hold — spends a single slot, its fields combined by
     * {@link #mergeResults}.
     */
    private List<CatalogResult> aggregate(List<CatalogProvider> providers, int limit,
            Function<CatalogProvider, List<CatalogResult>> call) {
        return aggregate(providers, limit, call, null);
    }

    /**
     * As above, but sorts the merged page by {@code relevance}, then by how complete each
     * record is, before trimming it to the limit; the round-robin order survives as the
     * tie-break. A {@code null} ranker (upcoming, an author's works) leaves the round-robin
     * order untouched. A free-text search does not come through here: {@link CatalogRanker}
     * ranks, groups and orders it.
     */
    private List<CatalogResult> aggregate(List<CatalogProvider> providers, int limit,
            Function<CatalogProvider, List<CatalogResult>> call,
            ToIntFunction<CatalogResult> relevance) {
        Map<String, CatalogResult> merged = new LinkedHashMap<>();
        for (CatalogResult r : roundRobin(callAll(providers, call))) {
            merged.merge(aggregationKey(r), r, CatalogService::mergeResults);
        }

        Stream<CatalogResult> ordered = merged.values().stream()
                .map(r -> isbnCovers ? withIsbnCover(r) : r);
        if (relevance != null) {
            // Equally relevant results are told apart by how much of a record they carry: an
            // edition with an ISBN and a cover is one a reader can recognise and add, where a
            // 19th-century notice with neither (the BnF holds plenty) is mostly noise.
            ordered = ordered.sorted(Comparator.comparingInt(relevance)
                    .thenComparingInt(CatalogService::completeness).reversed());
        }
        return ordered.limit(limit).toList();
    }

    /** Every provider's answers, one rank at a time: the first of each, then the second of each… */
    private static List<CatalogResult> roundRobin(List<List<CatalogResult>> perProvider) {
        int rounds = 0;
        for (List<CatalogResult> results : perProvider) {
            rounds = Math.max(rounds, results.size());
        }
        List<CatalogResult> merged = new ArrayList<>();
        for (int rank = 0; rank < rounds; rank++) {
            for (List<CatalogResult> results : perProvider) {
                if (rank < results.size()) {
                    merged.add(results.get(rank));
                }
            }
        }
        return merged;
    }

    private static int completeness(CatalogResult r) {
        return (r.isbn13() != null ? 1 : 0) + (r.coverUrl() != null ? 1 : 0);
    }

    /**
     * Calls every provider at once and waits for all of them, in the providers' order. They
     * used to be called one after the other, so a book search cost Open Library's latency
     * (often several seconds) <em>plus</em> the BnF's, and a cross-medium search added
     * AniList's on top; now it costs the slowest of them. Each call is already bounded by its
     * provider's own deadline and turns a failure into an empty list, so a join cannot hang
     * past that deadline nor throw. One provider is called inline, with no thread hop.
     */
    private static List<List<CatalogResult>> callAll(List<CatalogProvider> providers,
            Function<CatalogProvider, List<CatalogResult>> call) {
        if (providers.size() <= 1) {
            return providers.stream().map(call).toList();
        }
        List<CompletableFuture<List<CatalogResult>>> pending = providers.stream()
                .map(provider -> CompletableFuture.supplyAsync(() -> call.apply(provider), CALLS))
                .toList();
        List<List<CatalogResult>> results = new ArrayList<>(pending.size());
        for (CompletableFuture<List<CatalogResult>> future : pending) {
            try {
                List<CatalogResult> value = future.join();
                results.add(value == null ? List.of() : value);
            } catch (RuntimeException e) {
                results.add(List.of());
            }
        }
        return results;
    }

    /**
     * Virtual threads: a provider call spends its whole life waiting on the network, and the
     * cache underneath it uses plain JDBC and its own transaction, neither of which needs the
     * request's context.
     */
    private static final ExecutorService CALLS = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Gives a result with an ISBN and no cover the one Open Library serves by ISBN. The BnF
     * describes the French legal deposit — exactly the titles this application is for — and
     * carries no image at all, so a title only it knew always reached the screen blank. The
     * {@code default=false} flag makes Open Library answer 404 rather than a blank placeholder
     * when it has nothing, which the front end's cover already falls back from.
     */
    static CatalogResult withIsbnCover(CatalogResult r) {
        if ((r.coverUrl() != null && !r.coverUrl().isBlank()) || r.isbn13() == null
                || !r.isbn13().matches("\\d{13}")) {
            return r;
        }
        return new CatalogResult(r.kind(), r.title(), r.authors(), r.year(),
                "https://covers.openlibrary.org/b/isbn/" + r.isbn13() + "-M.jpg?default=false",
                r.synopsis(), r.isbn13(), r.publisher(), r.language(), r.releaseDate(),
                r.seriesTitle(), r.volumeNumber(), r.pageCount(), r.provider(), r.providerRef(),
                r.popularity());
    }

    /**
     * Deduplication key: title and author, accents folded, case dropped and every run of
     * punctuation or spacing reduced to one space, so "Astérix — Tome 1" and "Asterix, tome 1"
     * key alike and the same title from two catalogues spends a single slot. The BnF puts its
     * authority heading back in reading order for exactly this reason (see
     * CatalogBookAggregationTest).
     */
    private static String dedupKey(CatalogResult r) {
        return normalize(r.title()) + '|' + normalize(r.authors());
    }

    /**
     * Aggregation key: a result that carries a series and a volume — book catalogues parse both
     * out of the title (see {@link VolumeParser}), so every printing of "One Piece Tome 1" does,
     * whatever subtitle or author spelling it arrived with — keys on that series and volume, so
     * every edition of it collapses onto one slot. A volume-less result falls back to
     * {@link #dedupKey}, which keeps the author so two different books sharing a title stay apart.
     */
    private static String aggregationKey(CatalogResult r) {
        if (r.volumeNumber() != null && r.seriesTitle() != null && !r.seriesTitle().isBlank()) {
            return "series:" + normalize(r.seriesTitle()) + '#' + r.volumeNumber();
        }
        return dedupKey(r);
    }

    /**
     * Folds a duplicate edition into the one already kept. The incumbent is the round-robin
     * winner and stays the anchor — its provider reference, and so its "other editions" lookup —
     * but every field it lacks is filled from the newcomer and the larger page count wins. A
     * series+volume result takes a uniform "Series - Tome N" title, so the ranker scores it as the
     * clean match the reader typed rather than one catalogue's noisier wording.
     */
    private static CatalogResult mergeResults(CatalogResult kept, CatalogResult other) {
        String seriesTitle = firstNonBlank(kept.seriesTitle(), other.seriesTitle());
        Integer volume = kept.volumeNumber() != null ? kept.volumeNumber() : other.volumeNumber();
        String title = seriesTitle != null && volume != null
                ? seriesTitle + " - Tome " + volume
                : kept.title();
        return new CatalogResult(kept.kind(), title,
                readableAuthors(kept.authors(), other.authors()),
                kept.year() != null ? kept.year() : other.year(),
                firstNonBlank(kept.coverUrl(), other.coverUrl()),
                firstNonBlank(kept.synopsis(), other.synopsis()),
                firstNonBlank(kept.isbn13(), other.isbn13()),
                firstNonBlank(kept.publisher(), other.publisher()),
                firstNonBlank(kept.language(), other.language()),
                kept.releaseDate() != null ? kept.releaseDate() : other.releaseDate(),
                seriesTitle, volume, maxPageCount(kept.pageCount(), other.pageCount()),
                kept.provider(), kept.providerRef(),
                kept.popularity() != null ? kept.popularity() : other.popularity());
    }

    private static String readableAuthors(String kept, String other) {
        return CatalogRanker.readableAuthors(kept, other);
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static Integer maxPageCount(Integer a, Integer b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : Math.max(a, b);
    }

    /** Folds a label to its bare alphanumerics: NFD-stripped accents, lowercase, single spaces. */
    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String expanded = value.replace("Œ", "oe").replace("œ", "oe")
                .replace("Æ", "ae").replace("æ", "ae").replace("ß", "ss");
        String folded = Normalizer.normalize(expanded, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        return folded.replaceAll("[^a-z0-9]+", " ").trim();
    }
}
