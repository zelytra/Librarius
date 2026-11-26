package zelytra.librarius.imports;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jsoup.Jsoup;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the pages a reader collected in their own browser into titles to import.
 *
 * <p>The way a library gets out of a site that will not hand it to a server: Babelio shows it
 * only to its signed-in owner, Booknode turns servers away. The collector in Settings runs on
 * the reader's open session, walks every page of their library and saves the rows; this reads
 * them back with the same parsers a scrape uses, so a collected Booknode library and a scraped
 * one import identically.
 */
@ApplicationScoped
public class CollectedLibrary {

    /** Pages accepted in one file — a 2400-title Booknode library is 97 of them. */
    public static final int MAX_PAGES = 1000;

    @Inject
    BooknodeImporter booknode;

    public List<ImportedBook> parse(String source, List<String> pages) {
        if (pages == null || pages.isEmpty()) {
            throw new ImportException("Le fichier ne contient aucune page de bibliothèque.");
        }
        if (pages.size() > MAX_PAGES) {
            throw new ImportException("Le fichier contient trop de pages (" + pages.size() + ").");
        }
        // Mangacollec's collection is one JSON document, saved as the site's API gave it (#456).
        if (MangacollecImporter.SOURCE.equals(source)) {
            return MangacollecImporter.parse(pages.get(0));
        }
        List<ImportedBook> books = new ArrayList<>();
        List<org.jsoup.nodes.Document> documents = pages.stream()
                .filter(page -> page != null && !page.isBlank())
                .map(page -> Jsoup.parse(page, baseUrl(source)))
                .toList();
        // Once the file holds Babelio's library list, a page without it is not the library — a
        // selection the collector wandered into imported a hundred strangers' books (#502).
        boolean libraryList = "babelio".equals(source) && documents.stream().anyMatch(BabelioPageParser::isLibraryList);
        List<ImportedBook> history = new ArrayList<>();
        for (var document : documents) {
            if (libraryList && !BabelioPageParser.isLibraryList(document)) {
                continue;
            }
            // Booknode's reading history, collected with the library or on its own (#651).
            if ("booknode".equals(source) && BooknodeHistoryParser.isHistory(document)) {
                history.addAll(BooknodeHistoryParser.parse(document));
                continue;
            }
            books.addAll(switch (source) {
                case "booknode" -> booknode.parse(document);
                case "babelio" -> BabelioPageParser.parse(document);
                default -> throw new ImportException("Source d'import inconnue : " + source);
            });
        }
        books = datedByHistory(books, history);
        if (books.isEmpty()) {
            throw new ImportException("Aucun livre reconnu dans ce fichier. Lance la collecte depuis "
                    + "la page de ta bibliothèque, connecté à ton compte.");
        }
        return books;
    }

    /**
     * The library's read rows dated with the month the history gives them. A history entry is
     * the row of the same title whose authors include the one the history names — it names the
     * first alone, the library every one (81 of 414 entries on a real library, #651). The
     * history's other titles stay, to date a library imported before — they never add one
     * ({@link ImportedBook#datesOnly()}).
     */
    static List<ImportedBook> datedByHistory(List<ImportedBook> library, List<ImportedBook> history) {
        if (history.isEmpty()) {
            return library;
        }
        java.util.Map<String, List<ImportedBook>> byTitle = new java.util.LinkedHashMap<>();
        history.forEach(h -> byTitle.computeIfAbsent(fold(h.title()), t -> new ArrayList<>()).add(h));
        List<ImportedBook> dated = new ArrayList<>();
        for (ImportedBook b : library) {
            boolean finished = b.status() == zelytra.librarius.domain.LibraryStatus.READ
                    || b.status() == zelytra.librarius.domain.LibraryStatus.ABANDONED;
            ImportedBook read = null;
            List<ImportedBook> sameTitle = byTitle.get(fold(b.title()));
            if (!b.wished() && sameTitle != null) {
                for (ImportedBook h : sameTitle) {
                    if (namesAuthor(b.author(), h.author()) && (read == null || h.readAt().isAfter(read.readAt()))) {
                        read = h;
                    }
                }
                if (read != null) {
                    sameTitle.remove(read);
                }
            }
            dated.add(read == null || b.readAt() != null || !finished ? b
                    : new ImportedBook(b.title(), b.author(), b.coverUrl(), b.status(), b.shelf(), b.rating(),
                            b.acquiredAt(), read.readAt(), b.isbn13(), b.pageCount(), b.rank(), b.kind(), b.wished(),
                            b.ownership(), b.format(), true, false));
        }
        byTitle.values().forEach(dated::addAll);
        return dated;
    }

    /** Whether a credit line names an author — the history's one among the library's several. */
    static boolean namesAuthor(String authors, String author) {
        if (author == null || author.isBlank()) {
            return true;
        }
        return authors != null && fold(authors).contains(fold(author));
    }

    static String fold(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.FRENCH).trim();
    }

    private static String baseUrl(String source) {
        return "babelio".equals(source) ? "https://www.babelio.com/" : "https://booknode.com/";
    }
}
