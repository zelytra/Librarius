package zelytra.librarius.imports;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import zelytra.librarius.domain.LibraryStatus;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Booknode's reading history, {@code /profil/<pseudo>/historique} (#651): the titles a member read,
 * grouped by month — « novembre 2026 » — each with its tier. The library table carries no reading
 * date at all (#645), so this is the one place Booknode says when a book was read, and it says
 * the month alone: a title is dated the first of it, flagged {@link ImportedBook#readMonthOnly()}.
 *
 * <p>The page is drawn by the site's script, a year at a time as its heading is opened, so the
 * collector saves it once it has opened every year; this reads what it saved.
 */
final class BooknodeHistoryParser {

    private BooknodeHistoryParser() {
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    /** The Vue root a fetched history page carries its year's readings on, as JSON (#653). */
    private static Element root(Document page) {
        return page.selectFirst("[data-vue-app][data-current-year-details]");
    }

    /** Whether a page is the history — fetched, or saved by the collector — rather than the library table. */
    static boolean isHistory(Document page) {
        return root(page) != null || !page.select(".one_month .item").isEmpty();
    }

    /** The year a fetched history page shows, or null. */
    static Integer yearShown(Document page) {
        Element root = root(page);
        try {
            return root == null ? null : Integer.valueOf(root.attr("data-year").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Every year the member's history lists, newest first; empty when the page lists none. */
    static List<Integer> yearsListed(Document page) {
        Element root = root(page);
        List<Integer> years = new ArrayList<>();
        if (root == null) {
            return years;
        }
        try {
            for (var year : JSON.readTree(root.attr("data-table-years"))) {
                if (year.hasNonNull("year")) {
                    years.add(year.get("year").asInt());
                }
            }
        } catch (java.io.IOException e) {
            return years;
        }
        return years;
    }

    /**
     * One row per title, dated with its latest reading. The rows only date the library — they
     * never add a title to it ({@link ImportedBook#datesOnly()}).
     */
    static List<ImportedBook> parse(Document page) {
        Map<String, ImportedBook> latest = new LinkedHashMap<>();
        Element root = root(page);
        if (root != null) {
            readEmbedded(root, latest);
            return new ArrayList<>(latest.values());
        }
        for (Element month : page.select(".one_month")) {
            Element heading = month.selectFirst("h3");
            LocalDate first = heading == null ? null : firstOfMonth(heading.text());
            if (first == null) {
                continue;
            }
            for (Element item : month.select(".item")) {
                Element link = item.selectFirst(".panel-heading a");
                String title = link == null ? null : link.text().trim();
                if (title == null || title.isEmpty()) {
                    continue;
                }
                String author = labelled(item, "Auteur");
                String tier = tierCode(item);
                Set<String> classes = tier == null ? Set.of() : Set.of(tier);
                ImportedBook book = new ImportedBook(title, author, cover(item), LibraryStatus.READ, null,
                        BooknodeImporter.ratingFromBadge(classes), null, first, null, null,
                        BooknodeImporter.rankFromBadge(classes), null, false, null, null, true, true);
                keepLatest(latest, link.attr("href").isBlank() ? title + '|' + author : link.attr("href"), book);
            }
        }
        return new ArrayList<>(latest.values());
    }

    /**
     * The JSON a fetched page carries (#653): {@code data-current-year-details}, the year's
     * months and their {@code reads[{list_id, book}]}, the tiers being {@code data-lists}.
     */
    private static void readEmbedded(Element root, Map<String, ImportedBook> latest) {
        Integer year = yearShown(root.ownerDocument());
        if (year == null) {
            return;
        }
        try {
            readMonths(JSON.readTree(root.attr("data-current-year-details")), year,
                    JSON.readTree(root.attr("data-lists")), latest);
        } catch (java.io.IOException e) {
            // A page whose JSON does not read dates nothing; the library is imported regardless.
        }
    }

    /**
     * Another year, as the page's script fetches it when a year is opened (#653):
     * {@code GET /profil/<pseudo>/historique/<year>} answers {@code {"details": [months]}}, the
     * months of the current page's shape. The tiers are the history page's own {@code data-lists}.
     *
     * @throws java.io.IOException when the answer is not that JSON
     */
    static List<ImportedBook> parseYear(String json, int year, Document historyPage) throws java.io.IOException {
        var details = JSON.readTree(json).get("details");
        if (details == null || !details.isArray()) {
            throw new java.io.IOException("Not a year of the reading history");
        }
        Element root = root(historyPage);
        var lists = JSON.readTree(root == null ? "{}" : root.attr("data-lists"));
        Map<String, ImportedBook> latest = new LinkedHashMap<>();
        readMonths(details, year, lists, latest);
        return new ArrayList<>(latest.values());
    }

    private static void readMonths(com.fasterxml.jackson.databind.JsonNode months, int year,
            com.fasterxml.jackson.databind.JsonNode lists, Map<String, ImportedBook> latest) {
        for (var month : months) {
            LocalDate first = firstOfMonth(month.path("month_name").asText("") + " " + year);
            if (first == null) {
                continue;
            }
            for (var read : month.path("reads")) {
                var b = read.path("book");
                // Spaces collapsed as the rendered page shows them, the library row's spelling.
                String title = collapse(b.path("name").asText(""));
                if (title.isEmpty()) {
                    continue;
                }
                // The first author, as the rendered page names them: what a library row is matched on.
                var authors = b.path("authors");
                String author = authors.size() > 0 ? collapse(authors.get(0).path("nom").asText("")) : "";
                String tier = lists.path(read.path("list_id").asText()).path("badge_class").asText(null);
                Set<String> classes = tier == null ? Set.of() : Set.of(tier);
                String cover = b.path("cover_medium").asText(null);
                ImportedBook book = new ImportedBook(title, author.isEmpty() ? null : author, cover, LibraryStatus.READ,
                        null, BooknodeImporter.ratingFromBadge(classes), null, first, null, null,
                        BooknodeImporter.rankFromBadge(classes), null, false, null, null, true, true);
                String href = b.path("href").asText("");
                keepLatest(latest, href.isBlank() ? title + '|' + author : href, book);
            }
        }
    }

    private static String collapse(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private static void keepLatest(Map<String, ImportedBook> latest, String key, ImportedBook book) {
        ImportedBook known = latest.get(key);
        if (known == null || known.readAt().isBefore(book.readAt())) {
            latest.put(key, book);
        }
    }

    /** "novembre 2026" → 2026-11-01; anything else → null. */
    static LocalDate firstOfMonth(String label) {
        String[] parts = label.trim().toLowerCase(Locale.FRENCH).split("\\s+");
        if (parts.length != 2) {
            return null;
        }
        Integer month = ImportDates.FRENCH_MONTHS.get(parts[0]);
        try {
            return month == null ? null : LocalDate.of(Integer.parseInt(parts[1]), month, 1);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The value of a « Label : value » line of an entry. */
    private static String labelled(Element item, String label) {
        for (Element line : item.select(".box-text p")) {
            String text = line.text().trim();
            if (text.startsWith(label)) {
                String value = text.substring(text.indexOf(':') + 1).trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    /** The tier badge as the class name the library table gives it: « Pas apprécié » → pasapprecie. */
    private static String tierCode(Element item) {
        Element badge = item.selectFirst(".badge-paragraph .badge");
        if (badge == null) {
            return null;
        }
        return Normalizer.normalize(badge.text(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private static String cover(Element item) {
        Element img = item.selectFirst("img[data-src]");
        return img == null || img.attr("data-src").isBlank() ? null : img.attr("data-src");
    }
}
