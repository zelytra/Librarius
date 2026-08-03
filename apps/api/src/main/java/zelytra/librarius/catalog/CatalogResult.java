package zelytra.librarius.catalog;

import java.time.LocalDate;

/**
 * Normalized external catalog result, independent of the provider.
 * Used directly as the response DTO (serialized as-is).
 */
public record CatalogResult(
        String kind,
        String title,
        String authors,
        Integer year,
        String coverUrl,
        String synopsis,
        String isbn13,
        String publisher,
        String language,
        LocalDate releaseDate,
        String seriesTitle,
        Integer volumeNumber,
        Integer pageCount,
        String provider,
        String providerRef) {

    /**
     * The result a provider builds when it carries no series, volume or page count — AniList,
     * the edition lookups and every test fixture. Keeps those call sites at the original twelve
     * arguments while the canonical constructor gains the three enrichment fields.
     */
    public CatalogResult(String kind, String title, String authors, Integer year, String coverUrl,
            String synopsis, String isbn13, String publisher, String language,
            LocalDate releaseDate, String provider, String providerRef) {
        this(kind, title, authors, year, coverUrl, synopsis, isbn13, publisher, language,
                releaseDate, null, null, null, provider, providerRef);
    }
}
