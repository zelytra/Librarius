package zelytra.librarius.imports;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import io.quarkus.narayana.jta.QuarkusTransaction;
import zelytra.librarius.catalog.CatalogEntryService;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.domain.RankCategory;
import zelytra.librarius.domain.WishlistItem;
import zelytra.librarius.domain.ReadingProgress;
import zelytra.librarius.domain.repository.LibraryItemRepository;
import zelytra.librarius.domain.repository.RankCategoryRepository;
import zelytra.librarius.domain.repository.WishlistItemRepository;
import zelytra.librarius.domain.repository.ReadingProgressRepository;
import zelytra.librarius.web.ApiDtos.ManualBookDto;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    ReadingProgressRepository progresses;

    @Inject
    WishlistItemRepository wishes;

    @Inject
    zelytra.librarius.genre.GenreService genreService;

    @Inject
    MeterRegistry meters;

    /** Outcome of an import. */
    public record ImportResult(String source, int imported, int skipped, int total) {
    }

    public ImportResult importFromSource(String userId, String source, String handle) {
        return persist(userId, source, fetchBooks(source, handle));
    }

    /**
     * Reads the source's library into memory — the network-bound half of a scrape, kept apart
     * from {@link #persist} so a deferred import can walk the pages off the request thread
     * without a database transaction open across the outbound calls.
     */
    List<ImportedBook> fetchBooks(String source, String handle) {
        LibraryImporter importer = importers.stream()
                .filter(i -> i.source().equalsIgnoreCase(source))
                .findFirst()
                .orElseThrow(() -> new ImportException("Source d'import inconnue : " + source));
        return importer.fetch(handle);
    }

    public ImportResult importFromCsv(String userId, String csv) {
        return persist(userId, "csv", parseCsv(csv));
    }

    /** Marks a title this very import created: there is nothing in it yet to complete. */
    private static final UUID IMPORTED_IN_THIS_RUN = new UUID(0, 0);

    /** Titles written per transaction; see {@link #persist(String, String, List, Progress)}. */
    static final int BATCH = 100;

    /** Told how far an import has got, after each batch it commits. */
    @FunctionalInterface
    public interface Progress {
        void update(int imported, int skipped);
    }

    ImportResult persist(String userId, String source, List<ImportedBook> books) {
        return persist(userId, source, books, (imported, skipped) -> { });
    }

    /**
     * Writes the titles to the reader's library, {@value #BATCH} per transaction.
     *
     * <p>Not one transaction for the whole library: a 2400-title Booknode export takes about a
     * minute to write, and the transaction manager aborts any transaction past 60 seconds. One
     * transaction meant a big library crossed it, was rolled back whole, and the reader got
     * "import unavailable" with nothing saved — the smaller libraries the tests use never got
     * near it. In batches, each commit is a few seconds, the progress the screen polls moves as
     * the titles land, and a failure keeps what earlier batches wrote; running the import again
     * skips those as already present.
     */
    ImportResult persist(String userId, String source, List<ImportedBook> books, Progress progress) {
        Batches state = QuarkusTransaction.requiringNew().call(() -> prepare(userId, books));
        int imported = 0;
        int skipped = 0;
        for (int from = 0; from < books.size(); from += BATCH) {
            List<ImportedBook> slice = books.subList(from, Math.min(books.size(), from + BATCH));
            int[] counts = QuarkusTransaction.requiringNew().call(() -> persistBatch(userId, slice, state));
            imported += counts[0];
            skipped += counts[1];
            progress.update(imported, skipped);
        }

        meters.counter("librarius.import", "source", source).increment(imported);
        return new ImportResult(source, imported, skipped, books.size());
    }

    /** What every batch of one import shares: the titles already held, and the categories. */
    /**
     * What every batch of one import shares: the titles already held (by their key, with the
     * item each one is), the titles already wished for, and the categories.
     */
    private record Batches(Map<String, UUID> existing, Set<String> wished, Map<String, RankCategory> categories,
            int[] nextOrder, Map<String, Long> shelfCounts, long minForCategory) {
    }

    private Batches prepare(String userId, List<ImportedBook> books) {
        Map<String, UUID> existing = new HashMap<>();
        for (LibraryItem it : items.listByUser(userId)) {
            existing.putIfAbsent(key(it.edition.work.title, it.edition.work.authorsText), it.id);
        }
        Set<String> wished = new HashSet<>();
        for (WishlistItem wi : wishes.listForExport(userId)) {
            wished.add(key(wi.edition.work.title, wi.edition.work.authorsText));
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
        return new Batches(existing, wished, categories, nextOrder, shelfCounts, Math.max(1, books.size() / 300));
    }

    /**
     * One batch, in the caller's transaction: how many titles it imported, and skipped. A
     * skipped title is one already there — and is completed with what this import knows and
     * the library does not, so running a richer import again repairs an older one instead of
     * having to start the library over.
     */
    private int[] persistBatch(String userId, List<ImportedBook> books, Batches state) {
        int imported = 0;
        int skipped = 0;
        for (ImportedBook book : books) {
            if (book.title() == null || book.title().isBlank()) {
                continue;
            }
            ImportTitle parsed = ImportTitle.parse(book.title());
            String key = key(parsed.title(), book.author());
            UUID held = state.existing().get(key);
            if (held == null) {
                held = state.existing().get(key(book.title(), book.author()));
            }
            if (book.wished()) {
                // A wish for a title already held, or already wished for, adds nothing.
                if (held != null || !state.wished().add(key)) {
                    skipped++;
                } else {
                    WishlistItem wish = new WishlistItem();
                    wish.userId = userId;
                    wish.edition = catalog.createManualEdition(manualBook(book, parsed), userId);
                    wishes.persist(wish);
                    imported++;
                }
                continue;
            }
            if (held != null) {
                items.findOwned(userId, held).ifPresent(item -> complete(userId, item, book, parsed, state));
                skipped++;
                continue;
            }
            // Held from now on: a title listed twice in the same file is imported once.
            state.existing().put(key, IMPORTED_IN_THIS_RUN);

            LibraryStatus status = book.status() != null ? book.status() : LibraryStatus.TO_READ;
            LibraryItem item = new LibraryItem();
            item.userId = userId;
            item.edition = catalog.createManualEdition(manualBook(book, parsed), userId);
            item.status = status;
            if (book.ownership() != null) {
                item.ownership = book.ownership();
            }
            item.format = book.format();
            item.rating = book.rating();
            item.acquiredAt = book.acquiredAt();
            item.rankCategory = rankFor(userId, book, state);
            items.persist(item);
            recordReading(item, book.readAt());
            imported++;
        }
        return new int[] {imported, skipped};
    }

    /**
     * The entry an imported title is recorded as. A volume of a run joins its series: "One
     * Piece Tome 3" is volume 3 of One Piece, and Goodreads writes it "The Way of Kings (The
     * Stormlight Archive, #1)". No provider reference: Booknode is a shelf being scraped, not a
     * catalog that hands out identifiers.
     */
    private static ManualBookDto manualBook(ImportedBook book, ImportTitle parsed) {
        return new ManualBookDto(book.kind() != null ? book.kind() : Kind.BOOK,
                parsed.title(), book.author(), parsed.seriesTitle(), parsed.volumeNumber(),
                book.isbn13(), null, null, book.pageCount(), book.coverUrl(), null, null,
                null, null, ImportGenres.of(book.shelf(), book.kind()), null, null);
    }

    /**
     * The category an imported title files under: the source's own tier when it has one (a
     * Booknode "or" is the built-in Or the collection filters on), else its list, when that
     * list is a shelf of the reader's rather than the kind it names ("manga", "BD").
     */
    private RankCategory rankFor(String userId, ImportedBook book, Batches state) {
        RankCategory category;
        if (book.rank() != null) {
            category = ranks.findForUserByCode(userId, book.rank()).orElse(null);
        } else if (book.kind() != null) {
            category = null;
        } else {
            category = categoryFor(userId, book.shelf(), state.shelfCounts(), state.minForCategory(),
                    state.categories(), state.nextOrder());
        }
        // A category from an earlier batch is detached by now; the merge hands back the
        // instance this transaction tracks.
        return category == null || ranks.getEntityManager().contains(category)
                ? category : ranks.getEntityManager().merge(category);
    }

    /**
     * Fills in, on a title the library already holds, what this import knows and it does not.
     * Nothing the reader set is overwritten: a category is replaced only when it is the list an
     * earlier import filed the title under, a rating or a date only when there is none.
     */
    private void complete(String userId, LibraryItem item, ImportedBook book, ImportTitle parsed, Batches state) {
        RankCategory current = item.rankCategory;
        boolean fromImport = current == null
                || (!current.builtin && book.shelf() != null && current.code.equals(categoryCode(book.shelf())));
        if (book.rank() != null && fromImport) {
            item.rankCategory = rankFor(userId, book, state);
        }
        if (item.rating == null && book.rating() != null) {
            item.rating = book.rating();
        }
        if (item.acquiredAt == null && book.acquiredAt() != null) {
            item.acquiredAt = book.acquiredAt();
        }
        // Filed as a book by an import that could not tell a manga: moved to an entry of the
        // right kind, keeping what the old edition knew (its cover, its length, its ISBN).
        Kind kind = book.kind();
        if (kind != null && kind != Kind.BOOK && item.edition.work.kind == Kind.BOOK) {
            Edition old = item.edition;
            ManualBookDto dto = manualBook(book, parsed);
            item.edition = catalog.createManualEdition(new ManualBookDto(kind, dto.title(), dto.authors(),
                    dto.seriesTitle(), dto.volumeNumber(), old.isbn13, old.publisher, old.language,
                    old.pageCount, old.coverUrl != null ? old.coverUrl : dto.coverUrl(), null, null,
                    null, null, null, null, null));
        }
        // A work imported before lists were read as genres has none: the list's genre fills it.
        String genre = ImportGenres.of(book.shelf(), kind);
        if (genre != null && item.edition.work.genres.isEmpty()) {
            // A new set: a work created without genres holds the immutable empty one.
            item.edition.work.genres = new java.util.LinkedHashSet<>(genreService.resolve(genre));
            if (item.edition.work.genresText == null) {
                item.edition.work.genresText = genre;
            }
        }
        if (book.readAt() != null && (item.status == LibraryStatus.READ || item.status == LibraryStatus.ABANDONED)) {
            if (item.progress == null) {
                recordReading(item, book.readAt());
            } else if (item.progress.finishedAt == null) {
                item.progress.finishedAt = book.readAt();
            }
        }
    }

    /**
     * The reading the import vouches for. A title read (or given up) is finished, on the day the
     * export says when it says one: without this row it counted towards no goal, appeared in no
     * monthly statistic, and its Detail screen read "not started" next to a "read" badge. No
     * date is invented — {@code ReadingProgressService} stamps today, which for a book read in
     * 2019 and imported now would be wrong — so an undated read is complete and undated.
     */
    private void recordReading(LibraryItem item, LocalDate readAt) {
        if (item.status != LibraryStatus.READ && item.status != LibraryStatus.ABANDONED) {
            return;
        }
        ReadingProgress progress = new ReadingProgress();
        progress.libraryItem = item;
        progress.finishedAt = readAt;
        if (item.status == LibraryStatus.READ) {
            progress.percent = COMPLETE;
            progress.currentPage = item.effectivePageCount();
        }
        progresses.persist(progress);
        item.progress = progress;
    }

    /** A finished reading, in the percentage the progress is stored as. */
    private static final int COMPLETE = 100;

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
        // Two dates, kept apart: the day a book was finished is what the reading history, the
        // annual goal and the monthly statistics are built on, the day it was added is only
        // when it joined the shelf. A bare "date" column is read as the reading date for a
        // title marked read (or given up), and as the day it was added otherwise.
        int readDateCol = indexOf(header, "date read", "date de lecture", "lu le", "date de fin",
                "finished", "date lu");
        int addedDateCol = indexOf(header, "date added", "date d'ajout", "ajouté le", "acquis le");
        int dateCol = indexOf(header, "date");
        int isbnCol = indexOf(header, "isbn13", "isbn-13", "isbn", "ean");
        int pagesCol = indexOf(header, "number of pages", "pages", "nombre de pages", "nb pages");
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
            LibraryStatus status = mapCsvStatus(shelf);
            LocalDate explicitReadAt = ImportDates.parse(cell(cols, readDateCol));
            if (explicitReadAt != null && status == LibraryStatus.TO_READ) {
                // A dated reading is a book that was read, whatever custom shelf it sits on.
                status = LibraryStatus.READ;
            }
            LocalDate bareDate = ImportDates.parse(cell(cols, dateCol));
            boolean finished = status == LibraryStatus.READ || status == LibraryStatus.ABANDONED;
            LocalDate readAt = firstNonNull(explicitReadAt, finished ? bareDate : null);
            LocalDate addedAt = firstNonNull(ImportDates.parse(cell(cols, addedDateCol)),
                    finished ? null : bareDate);
            books.add(new ImportedBook(title, cell(cols, authorCol), null, status, shelf,
                    parseRating(cell(cols, ratingCol)), addedAt, readAt,
                    parseIsbn13(cell(cols, isbnCol)), parsePages(cell(cols, pagesCol))));
        }
        return books;
    }

    private static <T> T firstNonNull(T a, T b) {
        return a != null ? a : b;
    }

    /**
     * The ISBN-13 out of a cell. Goodreads writes it as a spreadsheet formula ({@code ="978…"}),
     * other exports with hyphens; only the digits count, and anything but thirteen of them
     * starting 978/979 is no ISBN-13.
     */
    static String parseIsbn13(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        return digits.length() == 13 && (digits.startsWith("978") || digits.startsWith("979"))
                ? digits : null;
    }

    private static Integer parsePages(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty() || digits.length() > 5) {
            return null;
        }
        int pages = Integer.parseInt(digits);
        return pages > 0 ? pages : null;
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
            return LibraryStatus.TO_READ;
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
            return LibraryStatus.TO_READ;
        }
        if (s.equals("lu") || s.contains("read") || s.contains("terminé")) {
            return LibraryStatus.READ;
        }
        return LibraryStatus.TO_READ;
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
