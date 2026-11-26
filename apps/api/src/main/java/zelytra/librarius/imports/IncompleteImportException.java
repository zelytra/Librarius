package zelytra.librarius.imports;

import java.util.List;

/**
 * A source that stopped answering part-way through a library (#644): the titles read before it
 * stopped are still worth importing, and the message tells the reader the library is not whole.
 */
public class IncompleteImportException extends ImportException {

    private final transient List<ImportedBook> books;

    public IncompleteImportException(List<ImportedBook> books, String message) {
        super(message);
        this.books = List.copyOf(books);
    }

    /** The titles read before the source stopped. */
    public List<ImportedBook> books() {
        return books;
    }
}
