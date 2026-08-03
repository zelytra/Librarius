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
import zelytra.librarius.domain.RankCategory;
import zelytra.librarius.domain.repository.LibraryItemRepository;
import zelytra.librarius.domain.repository.RankCategoryRepository;
import zelytra.librarius.web.ApiDtos.ManualBookDto;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Import orchestration: scraping or CSV -> creation of the owned titles. */
@ApplicationScoped
public class ImportService {

    @Inject
    Instance<LibraryImporter> importers;

    @Inject
    CatalogEntryService catalog;

    @Inject
    LibraryItemRepository items;

    @Inject
    RankCategoryRepository ranks;

    @Inject
    MeterRegistry meters;

    /** Outcome of an import. */
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
            existing.add(key(it.edition.work.title, it.edition.work.authorsText));
        }

        // Reuse the user's existing custom categories and order new ones after them, so a second
        // import does not duplicate a shelf and the categories keep a stable order.
        Map<String, RankCategory> categories = new HashMap<>();
        int[] nextOrder = {0};
        for (RankCategory c : ranks.listCustomForUser(userId)) {
            categories.put(c.code, c);
            nextOrder[0] = Math.max(nextOrder[0], c.sortOrder + 1);
        }

        // How many titles each list groups, and the floor a list has to clear to earn a
        // category. A big library — Booknode exports run to thousands — carries one-off lists
        // (a single author, a passing whim) that would otherwise each become a shelf and bury
        // the useful ones; a small CSV keeps every list, its floor being one. Roughly "a
        // noticeable share of the import": ~8 titles on a 2400-title library, one on a handful.
        Map<String, Long> shelfCounts = new HashMap<>();
        for (ImportedBook b : books) {
            if (b.shelf() != null && !b.shelf().isBlank()) {
                shelfCounts.merge(b.shelf().trim(), 1L, Long::sum);
            }
        }
        long minForCategory = Math.max(1, books.size() / 300);

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
            LibraryStatus status = book.status() != null ? book.status() : LibraryStatus.OWNED;
            // No provider reference: Booknode is a shelf being scraped, not a catalog that
            // hands out identifiers, and the two trailing nulls say so rather than filing the
            // import under a provider nothing can query.
            Edition edition = catalog.createManualEdition(new ManualBookDto(Kind.BOOK, book.title(),
                    book.author(), null, null, null, null, null, null, book.coverUrl(), null, null,
                    null, null, null, null, null));
            LibraryItem item = new LibraryItem();
            item.userId = userId;
            item.edition = edition;
            item.status = status;
            item.rating = book.rating();
            item.acquiredAt = book.acquiredAt();
            item.rankCategory = categoryFor(userId, book.shelf(), shelfCounts, minForCategory,
                    categories, nextOrder);
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

    /** Lenient CSV parser: recognizes title/author/status columns (FR and EN headers). */
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
        int ratingCol = indexOf(header, "note", "rating", "my rating", "note moyenne");
        int dateCol = indexOf(header, "date", "date read", "date added", "date de lecture", "date d'ajout");
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
            String shelf = cell(cols, statusCol);
            books.add(new ImportedBook(title, cell(cols, authorCol), null, mapCsvStatus(shelf),
                    shelf, parseRating(cell(cols, ratingCol)), ImportDates.parse(cell(cols, dateCol))));
        }
        return books;
    }

    /**
     * A rating scaled to the 1–5 the application stores. A value above five is taken to be out
     * of a larger scale — Babelio rates out of twenty — and divided down.
     */
    private static Integer parseRating(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            double v = Double.parseDouble(raw.trim().replace(',', '.'));
            if (v <= 0) {
                return null;
            }
            int scaled = v > 5 ? (int) Math.round(v / 4.0) : (int) Math.round(v);
            return Math.max(1, Math.min(5, scaled));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The category an imported title's list maps to, created on first sight. A category is a
     * genuine custom list ("romance", "favorites") — never a reading state, which is the item's
     * status and would only be duplicated as a shelf, and never a list too small to be worth
     * one. The decision is on the list itself, not the reading status, so a title that has been
     * read still lands in the list it was filed under (a read book can sit in "romance").
     */
    private RankCategory categoryFor(String userId, String shelf, Map<String, Long> counts,
            long minForCategory, Map<String, RankCategory> cache, int[] nextOrder) {
        if (shelf == null || shelf.isBlank() || isReadingStateWord(shelf)) {
            return null;
        }
        // Below the floor: a one-off list, not a shelf. The title still imports, uncategorised.
        if (counts.getOrDefault(shelf.trim(), 0L) < minForCategory) {
            return null;
        }
        String code = categoryCode(shelf);
        if (code.isEmpty()) {
            return null;
        }
        RankCategory cached = cache.get(code);
        if (cached != null) {
            return cached;
        }
        RankCategory resolved = ranks.findForUserByCode(userId, code).orElseGet(() -> {
            RankCategory created = new RankCategory();
            created.userId = userId;
            created.code = code;
            String label = shelf.trim();
            created.label = label.length() > 64 ? label.substring(0, 64) : label;
            created.sortOrder = nextOrder[0]++;
            created.builtin = false;
            ranks.persist(created);
            return created;
        });
        cache.put(code, resolved);
        return resolved;
    }

    /** The default reading shelves, whatever a source calls them — a status, not a category. */
    private static final Set<String> READING_STATE_WORDS = Set.of(
            "lu", "lus", "read", "terminé", "termine", "to-read", "to read", "à lire", "a lire",
            "pal", "reading", "currently-reading", "currently reading", "en cours",
            "en train de lire", "abandoned", "abandonné", "abandonne", "dnf", "envies", "envie",
            "wishlist");

    /**
     * Whether a list label is one of the reading states rather than a real list: those are the
     * item's status, and a category built from one would only duplicate it. Matches the default
     * names exactly, plus the few fragments — "lire", "cours", "abandon" — that only a status
     * carries, so a custom list ("romance", "jeunesse", "favorites") is never taken for one.
     */
    private static boolean isReadingStateWord(String shelf) {
        String s = shelf.toLowerCase(Locale.FRENCH).trim();
        return READING_STATE_WORDS.contains(s) || s.contains("lire") || s.contains("cours")
                || s.contains("abandon");
    }

    /** A shelf label folded to a short code: accents stripped, lowercased, non-alphanumerics dashed. */
    private static String categoryCode(String shelf) {
        String folded = Normalizer.normalize(shelf, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return folded.length() > 32 ? folded.substring(0, 32) : folded;
    }

    private static LibraryStatus mapCsvStatus(String raw) {
        if (raw == null) {
            return LibraryStatus.OWNED;
        }
        String s = raw.toLowerCase(Locale.FRENCH);
        // First, and before "cours": Goodreads has no abandoned shelf, so the wording is
        // whatever the exporting site chose — `abandoned` is what this application writes,
        // `dnf` ("did not finish") what the English-speaking sites settled on, and a French
        // export says "abandonné" or, more rarely, "lecture en cours abandonnée".
        if (s.contains("abandon") || s.equals("dnf") || s.contains("did not finish")
                || s.contains("did-not-finish")) {
            return LibraryStatus.ABANDONED;
        }
        if (s.contains("cours") || s.contains("reading") || s.contains("currently")) {
            return LibraryStatus.READING;
        }
        // Before the "read" test below, which these three would otherwise satisfy: the
        // Goodreads shelf a book sits on before it is opened is called `to-read`, and a
        // library exported from there — or from here — came back entirely marked as read.
        if (s.contains("to-read") || s.contains("to read") || s.contains("lire")) {
            return LibraryStatus.OWNED;
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
