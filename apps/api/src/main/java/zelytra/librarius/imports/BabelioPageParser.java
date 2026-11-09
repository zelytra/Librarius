package zelytra.librarius.imports;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import zelytra.librarius.domain.LibraryStatus;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a Babelio "Mes livres" page, as the reader's own browser collected it.
 *
 * <p>Babelio has no API and shows a member's library only to that member, signed in — no
 * server can fetch it. The collector in Settings runs in the reader's browser instead, on
 * their open session, and hands the rows of their library to this parser.
 *
 * <p>It keys on what is stable on Babelio rather than on a layout: a book is a link to
 * {@code /livres/<slug>/<id>}, an author a link to {@code /auteur/<slug>/<id>} — the shape of
 * every link to a book or an author across the site. A row is the smallest element holding a
 * book link; its reading status, rating and date are read from the row's own text and
 * attributes, each optional: a row with only a title and an author is still a book, which
 * the enricher then completes from the catalog.
 */
public final class BabelioPageParser {

    private static final Pattern DATE = Pattern.compile("\\b(\\d{2}/\\d{2}/\\d{4}|\\d{4}-\\d{2}-\\d{2})\\b");

    /** "4/5", "4,5 / 5": a rating written out. */
    private static final Pattern RATING_TEXT = Pattern.compile("(\\d(?:[.,]\\d)?)\\s*/\\s*5\\b");

    private BabelioPageParser() {
    }

    public static List<ImportedBook> parse(Document page) {
        List<ImportedBook> books = new ArrayList<>();
        Set<Element> rows = new LinkedHashSet<>();
        for (Element link : page.select("a[href*=/livres/]")) {
            rows.add(rowOf(link));
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Element row : rows) {
            ImportedBook book = toBook(row);
            if (book != null && seen.add(book.title() + '|' + book.author())) {
                books.add(book);
            }
        }
        return books;
    }

    /**
     * The row a book link sits in: the nearest table row or list item, or else the nearest
     * ancestor that also holds an author link — the smallest block describing one book.
     */
    private static Element rowOf(Element link) {
        Element row = link.closest("tr, li");
        if (row != null) {
            return row;
        }
        for (Element parent = link.parent(); parent != null; parent = parent.parent()) {
            if (!parent.select("a[href*=/auteur/]").isEmpty()) {
                return parent;
            }
        }
        return link;
    }

    private static ImportedBook toBook(Element row) {
        String title = null;
        for (Element link : row.select("a[href*=/livres/]")) {
            String text = link.text().trim();
            if (!text.isEmpty()) {
                title = text;
                break;
            }
        }
        if (title == null) {
            // Only a cover link: its alternative text is the title.
            Element img = row.selectFirst("a[href*=/livres/] img[alt]");
            title = img != null ? emptyToNull(img.attr("alt")) : null;
        }
        if (title == null) {
            return null;
        }
        Element authorLink = row.selectFirst("a[href*=/auteur/]");
        String author = authorLink != null ? emptyToNull(authorLink.text()) : null;
        String text = row.text();
        LibraryStatus status = status(row, text);
        if (status == null) {
            // A "Pense-bête" is a reminder of a book wanted, not one on the shelf.
            return null;
        }
        Element img = row.selectFirst("img[src]");
        String cover = img != null ? emptyToNull(img.absUrl("src").isEmpty() ? img.attr("src") : img.absUrl("src")) : null;
        Matcher date = DATE.matcher(text);
        return new ImportedBook(title, author, cover, status, null, rating(row, text),
                null, date.find() && status == LibraryStatus.READ ? ImportDates.parse(date.group(1)) : null,
                null, null);
    }

    /** The reading status a row names, from a status control's value or the row's words. */
    static LibraryStatus status(Element row, String text) {
        Element selected = row.selectFirst("select option[selected]");
        String raw = fold(selected != null ? selected.text() : text);
        if (raw.contains("pense-bete") || raw.contains("pense bete")) {
            return null;
        }
        if (raw.contains("abandon")) {
            return LibraryStatus.ABANDONED;
        }
        if (raw.contains("en cours")) {
            return LibraryStatus.READING;
        }
        if (raw.contains("a lire")) {
            return LibraryStatus.OWNED;
        }
        if (Pattern.compile("(^|[^a-z])lu([^a-z]|$)").matcher(raw).find()) {
            return LibraryStatus.READ;
        }
        return LibraryStatus.OWNED;
    }

    /** A star rating out of five: a rating widget's value, else a "4/5" in the text. */
    static Integer rating(Element row, String text) {
        for (String attribute : List.of("data-rateit-value", "data-rating", "data-note", "data-value")) {
            Element rated = row.selectFirst("[" + attribute + "]");
            if (rated != null) {
                Integer value = toFive(rated.attr(attribute));
                if (value != null) {
                    return value;
                }
            }
        }
        Matcher m = RATING_TEXT.matcher(text);
        return m.find() ? toFive(m.group(1)) : null;
    }

    private static Integer toFive(String raw) {
        try {
            double v = Double.parseDouble(raw.trim().replace(',', '.'));
            return v > 0 && v <= 5 ? (int) Math.max(1, Math.round(v)) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String fold(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.FRENCH);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
