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
        String provider,
        String providerRef) {
}
