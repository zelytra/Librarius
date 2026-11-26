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

    /** Whether a saved page is the history rather than the library table. */
    static boolean isHistory(Document page) {
        return !page.select(".one_month .item").isEmpty();
    }

    /**
     * One row per title, dated with its latest reading. The rows only date the library — they
     * never add a title to it ({@link ImportedBook#datesOnly()}).
     */
    static List<ImportedBook> parse(Document page) {
        Map<String, ImportedBook> latest = new LinkedHashMap<>();
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
                String key = link.attr("href").isBlank() ? title + '|' + author : link.attr("href");
                ImportedBook known = latest.get(key);
                if (known == null || known.readAt().isBefore(first)) {
                    latest.put(key, book);
                }
            }
        }
        return new ArrayList<>(latest.values());
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
