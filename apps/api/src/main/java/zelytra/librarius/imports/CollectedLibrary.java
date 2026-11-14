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
        for (String page : pages) {
            if (page == null || page.isBlank()) {
                continue;
            }
            var document = Jsoup.parse(page, baseUrl(source));
            books.addAll(switch (source) {
                case "booknode" -> booknode.parse(document);
                case "babelio" -> BabelioPageParser.parse(document);
                default -> throw new ImportException("Source d'import inconnue : " + source);
            });
        }
        if (books.isEmpty()) {
            throw new ImportException("Aucun livre reconnu dans ce fichier. Lance la collecte depuis "
                    + "la page de ta bibliothèque, connecté à ton compte.");
        }
        return books;
    }

    private static String baseUrl(String source) {
        return "babelio".equals(source) ? "https://www.babelio.com/" : "https://booknode.com/";
    }
}
