package zelytra.librarius.catalog;

import io.quarkus.cache.CacheResult;
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
 */
@ApplicationScoped
public class CatalogService {

    private final Map<Kind, List<CatalogProvider>> byKind = new EnumMap<>(Kind.class);

    @Inject
    public CatalogService(Instance<CatalogProvider> providers) {
        this(providers.stream().toList());
    }

    /** Test-friendly constructor (without CDI). */
    CatalogService(List<CatalogProvider> providers) {
        for (CatalogProvider provider : providers) {
            byKind.computeIfAbsent(provider.kind(), k -> new ArrayList<>()).add(provider);
        }
    }

    @CacheResult(cacheName = "catalog-search")
    public List<CatalogResult> search(Kind kind, String query, int limit) {
        return aggregate(kind, limit, provider -> provider.search(query, limit));
    }

    @CacheResult(cacheName = "catalog-upcoming")
    public List<CatalogResult> upcoming(Kind kind, int limit) {
        return aggregate(kind, limit, provider -> provider.upcoming(limit));
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
