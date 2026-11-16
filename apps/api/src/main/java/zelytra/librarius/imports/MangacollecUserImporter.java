package zelytra.librarius.imports;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Mangacollec by pseudo is gone (#503): it only ever worked for a public profile, and the
 * site's API stopped answering it. A collection is the reader's own, read in their browser
 * with their session by the collector; a request by pseudo is told where to go instead.
 */
@ApplicationScoped
public class MangacollecUserImporter implements LibraryImporter {

    @Override
    public String source() {
        return MangacollecImporter.SOURCE;
    }

    @Override
    public List<ImportedBook> fetch(String handle) {
        throw new ImportException("L'import Mangacollec par pseudo n'est plus disponible. Utilise le favori "
                + "« Librarius : collecter » sur mangacollec.com, connecté à ton compte : il lit ta collection "
                + "avec ta propre session.");
    }
}
