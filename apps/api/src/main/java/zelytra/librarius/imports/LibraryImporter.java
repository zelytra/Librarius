package zelytra.librarius.imports;

import java.util.List;

/** Source d'import (un site externe). */
public interface LibraryImporter {

    /** Identifiant de la source, ex. "booknode", "babelio". */
    String source();

    /** Récupère la bibliothèque publique de l'utilisateur {@code handle} (pseudo). */
    List<ImportedBook> fetch(String handle);
}
