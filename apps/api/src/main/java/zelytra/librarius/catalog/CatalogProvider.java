package zelytra.librarius.catalog;

import zelytra.librarius.domain.Kind;

import java.util.List;

/** Catalog provider for a given kind of work (book or manga). */
public interface CatalogProvider {

    /**
     * Stable identifier of the provider, matching the {@code provider} field it puts on its
     * results. Used as the cache key namespace, so it must not change without invalidating
     * what is already stored.
     */
    String name();

    /** Kind covered by this provider. */
    Kind kind();

    /** Search by title / author. */
    List<CatalogResult> search(String query, int limit);

    /** Known upcoming releases (best-effort, depending on the provider's data). */
    List<CatalogResult> upcoming(int limit);
}
