package zelytra.librarius.imports;

/** Erreur fonctionnelle d'import (message destiné à l'utilisateur). */
public class ImportException extends RuntimeException {
    public ImportException(String message) {
        super(message);
    }
}
