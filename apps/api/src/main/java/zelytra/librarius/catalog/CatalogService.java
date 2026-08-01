package zelytra.librarius.catalog;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import zelytra.librarius.domain.Kind;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

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
                () -> provider.search(query, limit)));
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
     * Merges every provider's results round-robin, one rank at a time, rather than
     * exhausting the first provider before touching the next: with a shared limit that
     * used to let whichever provider answered first (or simply returned more) fill the
     * whole page, crowding out a catalogue that may hold the only copy of a title.
     *
     * <p>A provider that returns fewer results (including none, e.g. down or off-topic)
     * simply drops out of later rounds, and the limit it leaves unused goes to the
     * others — nobody is penalized for another provider's silence. Dedup on
     * {@link #dedupKey} still happens as entries are merged, so two catalogues holding
     * the same title spend a single slot, not two.
     */
    private List<CatalogResult> aggregate(List<CatalogProvider> providers, int limit,
            Function<CatalogProvider, List<CatalogResult>> call) {
        List<List<CatalogResult>> perProvider = new ArrayList<>(providers.size());
        for (CatalogProvider provider : providers) {
            perProvider.add(call.apply(provider));
        }

        Map<String, CatalogResult> merged = new LinkedHashMap<>();
        for (int rank = 0; merged.size() < limit; rank++) {
            boolean anyProviderHadThisRank = false;
            for (List<CatalogResult> results : perProvider) {
                if (rank >= results.size()) {
                    continue;
                }
                anyProviderHadThisRank = true;
                merged.putIfAbsent(dedupKey(results.get(rank)), results.get(rank));
                if (merged.size() >= limit) {
                    break;
                }
            }
            if (!anyProviderHadThisRank) {
                break;
            }
        }
        return merged.values().stream().limit(limit).toList();
    }

    private static String dedupKey(CatalogResult r) {
        String title = r.title() == null ? "" : r.title();
        String authors = r.authors() == null ? "" : r.authors();
        return (title + '|' + authors).toLowerCase();
    }
}
