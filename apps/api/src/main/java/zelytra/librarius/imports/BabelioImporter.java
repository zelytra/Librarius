package zelytra.librarius.imports;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Babelio ne propose pas d'API et la bibliothèque d'un membre n'est pas
 * accessible publiquement sans connexion : le scraping anonyme par pseudo n'est
 * donc pas fiable. On expose la source pour l'orienter vers l'import par fichier.
 */
@ApplicationScoped
public class BabelioImporter implements LibraryImporter {

    @Override
    public String source() {
        return "babelio";
    }

    @Override
    public List<ImportedBook> fetch(String handle) {
        throw new ImportException("L'import Babelio par pseudo n'est pas disponible "
                + "(bibliothèque non publique). Exporte ta liste et utilise l'import par fichier (CSV).");
    }
}
