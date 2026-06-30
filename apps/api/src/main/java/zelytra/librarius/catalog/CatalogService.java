package zelytra.librarius.catalog;

import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import zelytra.librarius.domain.Kind;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Agrège les fournisseurs de catalogue et route les requêtes selon la nature
 * demandée. Les résultats sont mis en cache pour limiter la pression sur les
 * API externes (et respecter leurs quotas).
 */
@ApplicationScoped
public class CatalogService {

    private final Map<Kind, CatalogProvider> byKind = new EnumMap<>(Kind.class);

    @Inject
    public CatalogService(Instance<CatalogProvider> providers) {
        this(providers.stream().toList());
    }

    /** Constructeur testable (sans CDI). */
    CatalogService(List<CatalogProvider> providers) {
        for (CatalogProvider provider : providers) {
            byKind.putIfAbsent(provider.kind(), provider);
        }
    }

    @CacheResult(cacheName = "catalog-search")
    public List<CatalogResult> search(Kind kind, String query, int limit) {
        CatalogProvider provider = byKind.get(kind);
        return provider == null ? List.of() : provider.search(query, limit);
    }

    @CacheResult(cacheName = "catalog-upcoming")
    public List<CatalogResult> upcoming(Kind kind, int limit) {
        CatalogProvider provider = byKind.get(kind);
        return provider == null ? List.of() : provider.upcoming(limit);
    }
}
