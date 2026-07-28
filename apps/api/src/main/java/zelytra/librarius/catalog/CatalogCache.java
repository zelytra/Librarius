package zelytra.librarius.catalog;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Two-level cache in front of the catalog providers.
 *
 * <p>Level one is the existing Caffeine cache, local to the pod. Level two is the
 * {@code catalog_cache} table. A read walks them in that order and a fetch fills both, so a
 * pod that has just started answers from PostgreSQL instead of going back out to Open
 * Library or AniList — those quotas belong to the instance as a whole, and a deployment on
 * every merge to {@code main} used to reset them to zero several times a day.
 *
 * <p>The time-to-live belongs to the request type, not to the provider: six hours for a
 * search, twelve for the upcoming releases. Nothing here serves an expired entry — a
 * release date does move, and the point is to spare the providers, not to freeze the
 * catalog.
 *
 * <p>Hits and misses are counted <em>per level</em>
 * ({@code librarius_catalog_cache_lookups_total{level,result}}): a persistent level that
 * never hits would be a query added to every miss for nothing, and the metric is what tells
 * the two apart.
 */
@ApplicationScoped
public class CatalogCache {

    /** Request type: picks the first-level cache and the time-to-live. */
    public enum Scope {
        SEARCH,
        UPCOMING
    }

    @Inject
    @CacheName("catalog-search")
    Cache searchCache;

    @Inject
    @CacheName("catalog-upcoming")
    Cache upcomingCache;

    @Inject
    CatalogCacheStore store;

    @Inject
    MeterRegistry meters;

    /**
     * Time-to-live of the persistent level. Kept in step with the Caffeine
     * {@code expire-after-write} of the matching cache in {@code application.properties}:
     * the two levels expiring at different times would not be wrong, only confusing.
     */
    @ConfigProperty(name = "librarius.catalog.cache.search.ttl", defaultValue = "6H")
    Duration searchTtl;

    @ConfigProperty(name = "librarius.catalog.cache.upcoming.ttl", defaultValue = "12H")
    Duration upcomingTtl;

    /**
     * Returns the cached answer for {@code key}, calling {@code loader} only when neither
     * level holds a live one.
     *
     * @param scope    request type, which fixes the first-level cache and the time-to-live
     * @param provider provider the answer comes from, {@code openlibrary} or {@code anilist}
     * @param key      canonical request — operation, kind, query and limit
     */
    public List<CatalogResult> get(Scope scope, String provider, String key,
            Supplier<List<CatalogResult>> loader) {
        // The loader only runs when Caffeine has nothing, which is exactly the definition of
        // a first-level miss. Caffeine loads a given key once even under concurrent callers,
        // so a burst of identical searches inside a pod produces a single provider call.
        AtomicBoolean missed = new AtomicBoolean();
        List<CatalogResult> value = level1(scope)
                .<String, List<CatalogResult>>get(provider + '|' + key, ignored -> {
                    missed.set(true);
                    return throughStore(scope, provider, key, loader);
                })
                .await().indefinitely();
        count("memory", !missed.get());
        return value;
    }

    private List<CatalogResult> throughStore(Scope scope, String provider, String key,
            Supplier<List<CatalogResult>> loader) {
        CatalogCacheStore.Lookup lookup = store.loadOrFetch(provider, key, ttl(scope), loader);
        count("database", lookup.hit());
        return lookup.value();
    }

    /**
     * Drops the entries whose time-to-live has run out.
     *
     * <p>Expired rows are already excluded from every read, so this only reclaims space;
     * running it on the scheduler rather than on a request path is what keeps a growing
     * backlog of dead entries from ever showing up in a response time. {@code SKIP} means a
     * run that overruns its interval is not doubled up.
     */
    @Scheduled(every = "{librarius.catalog.cache.purge.every}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void purgeExpired() {
        int removed = store.purgeExpired();
        if (removed > 0) {
            meters.counter("librarius.catalog.cache.purged").increment(removed);
            Log.infof("Catalog cache: purged %d expired entries", removed);
        }
    }

    private void count(String level, boolean hit) {
        meters.counter("librarius.catalog.cache.lookups",
                "level", level,
                "result", hit ? "hit" : "miss").increment();
    }

    private Cache level1(Scope scope) {
        return scope == Scope.SEARCH ? searchCache : upcomingCache;
    }

    private Duration ttl(Scope scope) {
        return scope == Scope.SEARCH ? searchTtl : upcomingTtl;
    }
}
