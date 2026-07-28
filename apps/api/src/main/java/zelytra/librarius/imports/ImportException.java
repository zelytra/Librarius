package zelytra.librarius.imports;

/** Functional import error (the message is meant for the end user, hence in French). */
public class ImportException extends RuntimeException {
    public ImportException(String message) {
        super(message);
    }
}
