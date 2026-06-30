package zelytra.librarius.catalog;

import java.time.LocalDate;

/**
 * Résultat normalisé du catalogue externe, indépendant du fournisseur.
 * Sert directement de DTO de réponse (sérialisé tel quel).
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
