package zelytra.librarius.catalog;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import zelytra.librarius.domain.Kind;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Aggregates the catalog providers: for a given kind, queries every registered
 * provider (Open Library for books, AniList for mangas), then merges and
 * deduplicates the results. Results are cached to spare the external APIs.
 *
 * <p>The cache sits on the provider call rather than on the merged answer: the expensive
 * part is the outbound request, the merge is a walk over a few dozen records, and a
 * per-provider entry is what lets one provider's answer survive while another's expires.
 */
@ApplicationScoped
public class CatalogService {

    private final Map<Kind, List<CatalogProvider>> byKind = new EnumMap<>(Kind.class);

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
        }
    }

    public List<CatalogResult> search(Kind kind, CatalogQuery query, int limit) {
        return aggregate(kind, limit, provider -> cache.get(CatalogCache.Scope.SEARCH,
                provider.name(), "search|" + kind + '|' + query.cacheKey() + '|' + limit,
                () -> provider.search(query, limit)));
    }

    public List<CatalogResult> upcoming(Kind kind, int limit) {
        return aggregate(kind, limit, provider -> cache.get(CatalogCache.Scope.UPCOMING,
                provider.name(), "upcoming|" + kind + '|' + limit,
                () -> provider.upcoming(limit)));
    }

    private List<CatalogResult> aggregate(Kind kind, int limit,
            Function<CatalogProvider, List<CatalogResult>> call) {
        Map<String, CatalogResult> merged = new LinkedHashMap<>();
        for (CatalogProvider provider : byKind.getOrDefault(kind, List.of())) {
            for (CatalogResult result : call.apply(provider)) {
                merged.putIfAbsent(dedupKey(result), result);
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
