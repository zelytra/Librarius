package zelytra.librarius.imports;

import java.util.List;

/** Import source (an external site). */
public interface LibraryImporter {

    /** Source identifier, e.g. "booknode", "babelio". */
    String source();

    /** Fetches the public library of the user identified by {@code handle}. */
    List<ImportedBook> fetch(String handle);
}
