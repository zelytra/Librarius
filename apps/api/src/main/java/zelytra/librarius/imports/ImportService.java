package zelytra.librarius.imports;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import zelytra.librarius.catalog.CatalogEntryService;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.domain.repository.LibraryItemRepository;
import zelytra.librarius.web.ApiDtos.ManualBookDto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Orchestration de l'import : scraping ou CSV → création des titres possédés. */
@ApplicationScoped
public class ImportService {

    @Inject
    Instance<LibraryImporter> importers;

    @Inject
    CatalogEntryService catalog;

    @Inject
    LibraryItemRepository items;

    @Inject
    MeterRegistry meters;

    /** Résultat d'un import. */
    public record ImportResult(String source, int imported, int skipped, int total) {
    }

    public ImportResult importFromSource(String userId, String source, String handle) {
        LibraryImporter importer = importers.stream()
                .filter(i -> i.source().equalsIgnoreCase(source))
                .findFirst()
                .orElseThrow(() -> new ImportException("Source d'import inconnue : " + source));
        List<ImportedBook> books = importer.fetch(handle);
        return persist(userId, source, books);
    }

    public ImportResult importFromCsv(String userId, String csv) {
        return persist(userId, "csv", parseCsv(csv));
    }

    @Transactional
    ImportResult persist(String userId, String source, List<ImportedBook> books) {
        Set<String> existing = new HashSet<>();
        for (LibraryItem it : items.listByUser(userId)) {
            existing.add(key(it.edition.work.title, it.edition.work.authors));
        }

        int imported = 0;
        int skipped = 0;
        for (ImportedBook book : books) {
            if (book.title() == null || book.title().isBlank()) {
                continue;
            }
            if (!existing.add(key(book.title(), book.author()))) {
                skipped++;
                continue;
            }
            Edition edition = catalog.createManualEdition(new ManualBookDto(Kind.BOOK, book.title(),
                    book.author(), null, null, null, null, null, null, book.coverUrl(), null, null,
                    null, null, null));
            LibraryItem item = new LibraryItem();
            item.userId = userId;
            item.edition = edition;
            item.status = book.status() != null ? book.status() : LibraryStatus.OWNED;
            items.persist(item);
            imported++;
        }

        meters.counter("librarius.import", "source", source).increment(imported);
        return new ImportResult(source, imported, skipped, books.size());
    }

    private static String key(String title, String author) {
        return ((title == null ? "" : title) + '|' + (author == null ? "" : author))
                .toLowerCase(Locale.FRENCH).trim();
    }

    /** Parser CSV tolérant : reconnaît des colonnes titre/auteur/statut (FR & EN). */
    static List<ImportedBook> parseCsv(String csv) {
        List<ImportedBook> books = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return books;
        }
        String[] lines = csv.replace("\r", "").split("\n");
        char sep = lines[0].contains(";") ? ';' : ',';
        String[] header = splitCsv(lines[0], sep);
        int titleCol = indexOf(header, "titre", "title", "nom", "name");
        int authorCol = indexOf(header, "auteur", "author", "auteurs", "authors");
        int statusCol = indexOf(header, "statut", "status", "etagere", "étagère", "shelf", "exclusive shelf");
        boolean hasHeader = titleCol >= 0;
        int start = hasHeader ? 1 : 0;
        if (!hasHeader) {
            titleCol = 0;
            authorCol = header.length > 1 ? 1 : -1;
        }
        for (int i = start; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            String[] cols = splitCsv(lines[i], sep);
            String title = cell(cols, titleCol);
            if (title == null || title.isBlank()) {
                continue;
            }
            books.add(new ImportedBook(title, cell(cols, authorCol), null,
                    mapCsvStatus(cell(cols, statusCol))));
        }
        return books;
    }

    private static LibraryStatus mapCsvStatus(String raw) {
        if (raw == null) {
            return LibraryStatus.OWNED;
        }
        String s = raw.toLowerCase(Locale.FRENCH);
        if (s.contains("cours") || s.contains("reading") || s.contains("currently")) {
            return LibraryStatus.READING;
        }
        if (s.equals("lu") || s.contains("read") || s.contains("terminé")) {
            return LibraryStatus.READ;
        }
        return LibraryStatus.OWNED;
    }

    private static int indexOf(String[] header, String... names) {
        for (int i = 0; i < header.length; i++) {
            String h = header[i].toLowerCase(Locale.FRENCH).trim().replace("\"", "");
            for (String n : names) {
                if (h.equals(n)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String cell(String[] cols, int idx) {
        if (idx < 0 || idx >= cols.length) {
            return null;
        }
        String v = cols[idx].trim();
        if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
            v = v.substring(1, v.length() - 1);
        }
        return v.isBlank() ? null : v.trim();
    }

    private static String[] splitCsv(String line, char sep) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                cur.append(c);
            } else if (c == sep && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }
}
