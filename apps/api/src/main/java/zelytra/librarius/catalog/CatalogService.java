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
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

/**
 * Aggregates the catalog providers: for a set of kinds (or every registered one when
 * none is named), queries every matching provider (Open Library and the BnF for books,
 * AniList for mangas), then merges and deduplicates the results. Results are cached to
 * spare the external APIs.
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
        return aggregate(providersFor(kinds), limit, provider -> cache.get(CatalogCache.Scope.SEARCH,
                provider.name(), "search|" + provider.kind() + '|' + query.cacheKey() + '|' + limit,
                () -> provider.search(query, limit)), relevanceScorer(query));
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
        return aggregate(providersFor(kinds), limit, provider -> cache.get(CatalogCache.Scope.SEARCH,
                provider.name(), "authorworks|" + key + '|' + limit,
                () -> provider.worksOfAuthor(name, limit)));
    }

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
     * As above, but re-ranks the merged page by {@code relevance} before trimming it to the
     * limit: the results that actually match the query rise to the top, whatever provider or
     * rank they came in at, while the round-robin order survives as the tie-break between
     * equally-relevant entries. The whole candidate pool is ranked, not just the first
     * {@code limit} the round-robin happened to reach, so a strong match sitting deep in one
     * provider's answer is no longer dropped for a weak one another returned early. A
     * {@code null} ranker (upcoming, editions) leaves the round-robin order untouched.
     */
    private List<CatalogResult> aggregate(List<CatalogProvider> providers, int limit,
            Function<CatalogProvider, List<CatalogResult>> call,
            ToIntFunction<CatalogResult> relevance) {
        List<List<CatalogResult>> perProvider = new ArrayList<>(providers.size());
        int rounds = 0;
        for (CatalogProvider provider : providers) {
            List<CatalogResult> results = call.apply(provider);
            perProvider.add(results);
            rounds = Math.max(rounds, results.size());
        }

        Map<String, CatalogResult> merged = new LinkedHashMap<>();
        for (int rank = 0; rank < rounds; rank++) {
            for (List<CatalogResult> results : perProvider) {
                if (rank < results.size()) {
                    CatalogResult r = results.get(rank);
                    merged.merge(aggregationKey(r), r, CatalogService::mergeResults);
                }
            }
        }

        Stream<CatalogResult> ordered = merged.values().stream();
        if (relevance != null) {
            ordered = ordered.sorted(Comparator.comparingInt(relevance).reversed());
        }
        return ordered.limit(limit).toList();
    }

    /**
     * Scores a result against the free-text query so the merge can promote a genuine match:
     * an exact title first, then a title carrying every word of the query, then some of them,
     * then the rest. Only the text criterion drives it — an author, ISBN or year search leaves
     * every result equal, so the providers' own order (and the round-robin fairness) stands.
     */
    private static ToIntFunction<CatalogResult> relevanceScorer(CatalogQuery query) {
        String needle = normalize(query.text());
        if (needle.isEmpty()) {
            return result -> 0;
        }
        List<String> words = List.of(needle.split(" "));
        return result -> {
            String title = normalize(result.title());
            if (title.equals(needle)) {
                return 3;
            }
            if (title.startsWith(needle)) {
                return 2;
            }
            long present = words.stream().filter(title::contains).count();
            if (present == words.size()) {
                return 2;
            }
            return present > 0 ? 1 : 0;
        };
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
                firstNonBlank(kept.authors(), other.authors()),
                kept.year() != null ? kept.year() : other.year(),
                firstNonBlank(kept.coverUrl(), other.coverUrl()),
                firstNonBlank(kept.synopsis(), other.synopsis()),
                firstNonBlank(kept.isbn13(), other.isbn13()),
                firstNonBlank(kept.publisher(), other.publisher()),
                firstNonBlank(kept.language(), other.language()),
                kept.releaseDate() != null ? kept.releaseDate() : other.releaseDate(),
                seriesTitle, volume, maxPageCount(kept.pageCount(), other.pageCount()),
                kept.provider(), kept.providerRef());
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
