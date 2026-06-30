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
 * Agrège les fournisseurs de catalogue : pour une nature donnée, interroge tous
 * les fournisseurs (ex. Open Library + Google Books pour les livres), fusionne
 * et dédoublonne. Les résultats sont mis en cache pour ménager les API externes.
 */
@ApplicationScoped
public class CatalogService {

    private final Map<Kind, List<CatalogProvider>> byKind = new EnumMap<>(Kind.class);

    @Inject
    public CatalogService(Instance<CatalogProvider> providers) {
        this(providers.stream().toList());
    }

    /** Constructeur testable (sans CDI). */
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
