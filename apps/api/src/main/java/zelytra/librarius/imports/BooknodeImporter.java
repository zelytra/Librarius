package zelytra.librarius.imports;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import zelytra.librarius.domain.LibraryStatus;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Imports the public library of a Booknode member from its server-rendered "biblio-table"
 * view — one row per title (cover, name, author, shelf, date), parsed with Jsoup.
 *
 * <p>Two things a naive scrape got wrong and this does not:
 * <ul>
 *   <li><strong>Booknode answers a non-browser User-Agent with a 403</strong>, so every request
 *       carries a real browser's headers rather than a bot signature.</li>
 *   <li><strong>The table is paginated</strong>, so a heavy reader's library spans dozens of
 *       pages (a 2400-title shelf is 97 of them). The first page's pagination names how many,
 *       and every page is fetched rather than only the first.</li>
 * </ul>
 */
@ApplicationScoped
public class BooknodeImporter implements LibraryImporter {

    /** A real browser signature: Booknode returns 403 to anything that looks like a bot. */
    private static final String UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/126.0.0.0 Safari/537.36";

    /** Hard cap on pages fetched, so a malformed pagination cannot loop unbounded. */
    private static final int MAX_PAGES = 400;

    private static final Pattern PAGE_PARAM = Pattern.compile("[?&]page=(\\d+)");

    @Override
    public String source() {
        return "booknode";
    }

    @Override
    public List<ImportedBook> fetch(String handle) {
        String h = handle.trim();
        List<ImportedBook> all = new ArrayList<>();

        Document first;
        try {
            first = get(profileUrl(h, 1));
        } catch (Exception e) {
            Log.warnf("Booknode import failed for %s: %s", h, e.getMessage());
            throw new ImportException("Impossible de lire la bibliothèque Booknode de « " + h
                    + " ». Vérifie le pseudo et que le profil est public.");
        }
        all.addAll(parse(first));

        int pages = Math.min(highestPage(first), MAX_PAGES);
        for (int page = 2; page <= pages; page++) {
            try {
                all.addAll(parse(get(profileUrl(h, page))));
            } catch (Exception e) {
                // A page failing mid-way (a transient block) must not throw the whole import
                // away: keep everything the earlier pages produced.
                Log.warnf("Booknode import stopped at page %d/%d for %s: %s", page, pages, h,
                        e.getMessage());
                break;
            }
        }
        return all;
    }

    private static String profileUrl(String handle, int page) {
        return "https://booknode.com/profil/" + handle + "/biblio-table?page=" + page;
    }

    private static Document get(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent(UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.8")
                .header("Upgrade-Insecure-Requests", "1")
                .timeout(20000)
                .get();
    }

    /** The highest page the pagination links to — at least 1. Package-private for the test. */
    static int highestPage(Document firstPage) {
        int max = 1;
        for (Element a : firstPage.select(".pagination a[href*=page=]")) {
            Matcher m = PAGE_PARAM.matcher(a.attr("href"));
            if (m.find()) {
                max = Math.max(max, Integer.parseInt(m.group(1)));
            }
        }
        return max;
    }

    /** Pure parsing of one page (tested against a fixture, without network access). */
    List<ImportedBook> parse(Document doc) {
        List<ImportedBook> books = new ArrayList<>();
        // Data rows are `.book-item`; the `.book-header` row is not one, so the column titles
        // ("Nom", "Auteur(s)"…) are skipped without a special case.
        for (Element row : doc.select(".book-item")) {
            String title = textOf(row, ".book-name");
            if (title == null || title.isBlank()) {
                continue;
            }
            // The `.list-name` cell is not a shelf name but a badge whose CSS class carries the
            // reading status, and — for a read book — Booknode's rating tier (diamant, or,
            // argent, bronze). What a naive scrape read as the shelf ("lu aussi", "or", …) was
            // therefore never a status word, which is why every read title came back "to read".
            Element badge = row.selectFirst(".list-name .listebadge");
            Set<String> badgeClasses = badge != null ? badge.classNames() : Set.of();
            String badgeText = badge != null ? badge.text() : textOf(row, ".list-name");
            LibraryStatus status = statusFromBadge(badgeClasses, badgeText);
            if (status == null) {
                // A Booknode "Envie" is a want, not a title in the library: skipped rather than
                // imported as owned, which would inflate the collection with books not held.
                continue;
            }
            // The list a title was filed under — `.group-name`, a genuine custom shelf — is the
            // one that becomes a category, not the rating tier the badge above encodes.
            String list = emptyToNull(textOf(row, ".group-name"));
            LocalDate acquiredAt = ImportDates.parse(textOf(row, ".date-added"));
            books.add(new ImportedBook(title, emptyToNull(textOf(row, ".author-name")),
                    coverNear(row), status, list, ratingFromBadge(badgeClasses), acquiredAt));
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

    /** Booknode's read-rating tiers, best to worst: any of them means the book has been read. */
    private static final Set<String> READ_TIERS = Set.of("diamant", "or", "argent", "bronze");

    /**
     * The reading status a Booknode badge stands for, read from its CSS class first (the class
     * is stable; the visible text is localised and decorated) and its text as a fallback. The
     * rating tiers and "lu aussi" are all read; "en cours" is being read; "abandonné" given up
     * on; a want ("Envies") is not a library title at all and answers {@code null} so the
     * caller can drop it; everything else — "À lire" and its kin — is owned but unread.
     */
    private static LibraryStatus statusFromBadge(Set<String> classes, String text) {
        String t = text == null ? "" : text.toLowerCase(Locale.FRENCH).trim();
        // Abandoned first: "lecture abandonnée" contains "lecture", which the reading test
        // below would otherwise claim.
        if (classes.contains("abandonne") || t.contains("abandon")) {
            return LibraryStatus.ABANDONED;
        }
        if (classes.contains("encours") || t.contains("en cours") || t.contains("train de lire")
                || t.contains("cours de lecture")) {
            return LibraryStatus.READING;
        }
        if (classes.contains("luaussi") || t.startsWith("lu") || t.contains("coup de c")
                || classes.stream().anyMatch(READ_TIERS::contains)) {
            return LibraryStatus.READ;
        }
        if (classes.contains("envies") || t.contains("envie")) {
            return null;
        }
        return LibraryStatus.OWNED;
    }

    /** The rating a read book's tier badge stands for, out of five; null when it carries none. */
    private static Integer ratingFromBadge(Set<String> classes) {
        if (classes.contains("diamant")) {
            return 5;
        }
        if (classes.contains("or")) {
            return 4;
        }
        if (classes.contains("argent")) {
            return 3;
        }
        if (classes.contains("bronze")) {
            return 2;
        }
        return null;
    }

    private static String emptyToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }
}
