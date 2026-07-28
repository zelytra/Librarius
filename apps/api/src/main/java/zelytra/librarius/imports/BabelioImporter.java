package zelytra.librarius.imports;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Babelio offers no API and a member's library is not publicly reachable without
 * being logged in: anonymous scraping by handle is therefore unreliable. The source
 * is still exposed so that users can be pointed to the file import instead.
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
