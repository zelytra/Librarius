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

    /**
     * Searches the provider for the criteria it understands, ignoring the others: a
     * publisher means nothing to AniList, and returning nothing because of it would be
     * worse than answering on the criteria that do apply.
     */
    List<CatalogResult> search(CatalogQuery query, int limit);

    /** Known upcoming releases (best-effort, depending on the provider's data). */
    List<CatalogResult> upcoming(int limit);

    /**
     * The other editions the provider knows of one of its works, keyed by the reference the
     * work was stored with ({@code work.provider_ref}). Best-effort catalog data: the results
     * are one materialisation each — publisher, ISBN, language, cover — and nothing is
     * persisted here.
     *
     * <p>The default answers nothing, which is the honest answer for a provider that exposes
     * no per-work edition list, or that hands out no usable reference to key one on — Open
     * Library and AniList as they stand. A caller merges what it gets, and a work with no
     * reference never reaches this method at all.
     */
    default List<CatalogResult> editionsOf(String workRef, int limit) {
        return List.of();
    }

    /**
     * The number of volumes a series runs to, by title — AniList knows it for a manga; a book
     * catalogue does not, hence the default. Read off the request path to fill
     * {@code series.total_volumes}, never on a render.
     */
    default java.util.OptionalInt seriesVolumes(String title) {
        return java.util.OptionalInt.empty();
    }

    /**
     * Every work the provider credits to an author, by name. The default runs the provider's
     * own search on the author term alone — Open Library and the BnF both index works by
     * author, so their bibliography <em>is</em> that search, which is why a book author page
     * would otherwise show only the volumes already owned. AniList overrides this with a
     * staff-linked lookup, its author index being a first-class one. Best-effort catalog data,
     * merged with what the local catalog already holds.
     */
    default List<CatalogResult> worksOfAuthor(String authorName, int limit) {
        if (authorName == null || authorName.isBlank()) {
            return List.of();
        }
        return search(new CatalogQuery(null, authorName, null, null, null, null), limit);
    }
}
