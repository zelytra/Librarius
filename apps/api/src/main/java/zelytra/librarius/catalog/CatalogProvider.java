package zelytra.librarius.catalog;

import zelytra.librarius.domain.Kind;

import java.util.List;

/** Fournisseur de catalogue pour une nature d'œuvre donnée (livre ou manga). */
public interface CatalogProvider {

    /** Nature couverte par ce fournisseur. */
    Kind kind();

    /** Recherche par titre / auteur. */
    List<CatalogResult> search(String query, int limit);

    /** Prochaines sorties connues (best-effort selon les données du fournisseur). */
    List<CatalogResult> upcoming(int limit);
}
