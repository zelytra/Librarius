package zelytra.librarius.imports;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import zelytra.librarius.domain.LibraryStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Importe la bibliothèque publique d'un membre Booknode. La vue « biblio-table »
 * est rendue côté serveur : on parse chaque entrée (titre, auteur, couverture,
 * étagère) avec Jsoup.
 */
@ApplicationScoped
public class BooknodeImporter implements LibraryImporter {

    private static final String UA =
            "Mozilla/5.0 (compatible; LibrariusImporter/1.0; +https://book.zelytra.fr)";

    @Override
    public String source() {
        return "booknode";
    }

    @Override
    public List<ImportedBook> fetch(String handle) {
        String url = "https://booknode.com/profil/" + handle.trim() + "/biblio-table";
        try {
            Document doc = Jsoup.connect(url).userAgent(UA).timeout(15000).get();
            return parse(doc);
        } catch (Exception e) {
            Log.warnf("Import Booknode échoué pour %s : %s", handle, e.getMessage());
            throw new ImportException("Impossible de lire la bibliothèque Booknode de « " + handle
                    + " ». Vérifie le pseudo et que le profil est public.");
        }
    }

    /** Parsing pur (testé sur fixture, sans réseau). */
    List<ImportedBook> parse(Document doc) {
        List<ImportedBook> books = new ArrayList<>();
        for (Element nameEl : doc.select(".book-name")) {
            String title = nameEl.text().trim();
            if (title.isEmpty()) {
                continue;
            }
            Element row = nameEl.parent();
            String author = textOf(row, ".author-name");
            String shelf = textOf(row, ".list-name");
            String cover = coverNear(row);
            books.add(new ImportedBook(title, emptyToNull(author), cover, mapStatus(shelf)));
        }
        return books;
    }

    private static String textOf(Element scope, String selector) {
        if (scope == null) {
            return null;
        }
        Element el = scope.selectFirst(selector);
        return el != null ? el.text().trim() : null;
    }

    private static String coverNear(Element row) {
        if (row == null) {
            return null;
        }
        Element img = row.selectFirst("img[data-src]");
        if (img == null) {
            img = row.selectFirst("img[src]");
        }
        if (img == null) {
            return null;
        }
        String src = img.hasAttr("data-src") ? img.attr("data-src") : img.attr("src");
        return src.isBlank() ? null : src;
    }

    private static LibraryStatus mapStatus(String shelf) {
        if (shelf == null) {
            return LibraryStatus.OWNED;
        }
        String s = shelf.toLowerCase(Locale.FRENCH);
        if (s.contains("train de lire") || s.contains("en cours")) {
            return LibraryStatus.READING;
        }
        // « Lu » mais pas « à lire » / « relire ».
        if (s.contains("lu") && !s.contains("à lire") && !s.contains("relire")) {
            return LibraryStatus.READ;
        }
        return LibraryStatus.OWNED;
    }

    private static String emptyToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }
}
