package zelytra.librarius.imports;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import zelytra.librarius.domain.BookFormat;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.domain.Ownership;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a Mangacollec collection (#448): the JSON its site loads for a signed-in member, which
 * the reader saves from their browser and hands over as a file.
 *
 * <p>The document is normalised — flat arrays joined by id: {@code series} → {@code editions}
 * → {@code volumes}, and the reader's own {@code possessions} (volumes held) and {@code reads}
 * (volumes read), plus {@code boxes} and {@code box_possessions} for box sets. Every volume held
 * or read becomes one title, "Série Tome n", which the import files as volume n of the series,
 * with its ISBN and cover. A volume read without being held is read and not owned; one held and
 * not read is to read. Mangacollec names no author: the enrichment that follows an import finds
 * them from the ISBN.
 */
public final class MangacollecImporter {

    public static final String SOURCE = "mangacollec";

    private static final ObjectMapper JSON = new ObjectMapper();

    private MangacollecImporter() {
    }

    /** Whether {@code root} has the shape of a Mangacollec collection. */
    static boolean looksLikeCollection(JsonNode root) {
        return root != null && root.path("volumes").isArray() && root.path("editions").isArray()
                && (root.path("possessions").isArray() || root.path("reads").isArray());
    }

    public static List<ImportedBook> parse(String body) {
        JsonNode root;
        try {
            root = body == null ? null : JSON.readTree(body);
        } catch (JsonProcessingException e) {
            root = null;
        }
        if (!looksLikeCollection(root)) {
            throw new ImportException("Ce fichier n'est pas une collection Mangacollec.");
        }
        Map<String, JsonNode> types = byId(root.path("types"));
        Map<String, JsonNode> series = byId(root.path("series"));
        Map<String, JsonNode> editions = byId(root.path("editions"));
        Map<String, LocalDate> owned = datesByKey(root.path("possessions"), "volume_id");
        Map<String, LocalDate> read = datesByKey(root.path("reads"), "volume_id");

        List<ImportedBook> books = new ArrayList<>();
        for (JsonNode volume : root.path("volumes")) {
            String id = volume.path("id").asText(null);
            if (id == null || !(owned.containsKey(id) || read.containsKey(id))) {
                continue;
            }
            JsonNode edition = editions.get(volume.path("edition_id").asText(""));
            JsonNode serie = edition == null ? null : series.get(edition.path("series_id").asText(""));
            String seriesTitle = text(serie, "title");
            if (seriesTitle == null) {
                continue;
            }
            String editionTitle = text(edition, "title");
            String name = editionTitle == null ? seriesTitle : seriesTitle + " " + editionTitle;
            String title = name + volumePart(volume, edition);
            String volumeTitle = text(volume, "title");
            if (volumeTitle != null) {
                title += " — " + volumeTitle;
            }
            books.add(book(title, volume, owned.containsKey(id), owned.get(id), read.containsKey(id), read.get(id),
                    kindOf(types.get(text(serie, "type_id"))), formatOf(editionTitle)));
        }

        Map<String, JsonNode> boxEditions = byId(root.path("box_editions"));
        Map<String, LocalDate> boxDates = datesByKey(root.path("box_possessions"), "box_id");
        for (JsonNode box : root.path("boxes")) {
            String id = box.path("id").asText(null);
            if (id == null || !boxDates.containsKey(id)) {
                continue;
            }
            String boxTitle = text(box, "title");
            String editionTitle = text(boxEditions.get(box.path("box_edition_id").asText("")), "title");
            String title = editionTitle == null ? boxTitle
                    : boxTitle == null ? editionTitle : editionTitle + " " + boxTitle;
            if (title == null) {
                continue;
            }
            books.add(book(title, box, true, boxDates.get(id), false, null, Kind.MANGA, BookFormat.BOX_SET));
        }

        if (books.isEmpty()) {
            throw new ImportException("Aucun tome possédé ni lu dans cette collection Mangacollec.");
        }
        return books;
    }

    private static ImportedBook book(String title, JsonNode volume, boolean isOwned, LocalDate acquiredAt,
            boolean isRead, LocalDate readAt, Kind kind, BookFormat format) {
        return new ImportedBook(title, null, text(volume, "image_url"),
                isRead ? LibraryStatus.READ : LibraryStatus.TO_READ, null, null, acquiredAt,
                isRead ? readAt : null, isbn13(text(volume, "isbn")), null, null, kind, false,
                isOwned ? Ownership.OWNED : Ownership.NOT_OWNED, format);
    }

    /**
     * " Tome n", or nothing for a one-shot: Mangacollec numbers the single volume of a one-shot
     * 0 or 1 in an edition of one, and "L'Amour et la Mort Tome 0" is no title anyone writes.
     */
    private static String volumePart(JsonNode volume, JsonNode edition) {
        JsonNode number = volume.path("number");
        if (!number.isNumber()) {
            return "";
        }
        int n = number.asInt();
        boolean oneShot = edition != null && edition.path("volumes_count").asInt(0) <= 1;
        return oneShot && n <= 1 ? "" : " Tome " + n;
    }

    /** Mangacollec's types: "Manga", "Global Manga", "Manhwa"… are manga; "Roman" a book. */
    static Kind kindOf(JsonNode type) {
        String t = text(type, "title");
        if (t == null) {
            return Kind.MANGA;
        }
        String l = t.toLowerCase(Locale.FRENCH);
        if (l.contains("roman") || l.contains("novel")) {
            return Kind.BOOK;
        }
        if (l.contains("comic") || l.equals("bd")) {
            return Kind.COMIC;
        }
        return Kind.MANGA;
    }

    /** The format an edition's name tells — "Intégral Deluxe", "Perfect Edition", "Coffret". */
    static BookFormat formatOf(String editionTitle) {
        if (editionTitle == null) {
            return null;
        }
        String l = editionTitle.toLowerCase(Locale.FRENCH);
        if (l.contains("coffret")) {
            return BookFormat.BOX_SET;
        }
        if (l.contains("deluxe") || l.contains("perfect") || l.contains("collector")
                || l.contains("ultimate") || l.contains("intégrale") || l.contains("intégral")) {
            return BookFormat.DELUXE;
        }
        if (l.contains("relié")) {
            return BookFormat.HARDCOVER;
        }
        if (l.contains("poche") || l.contains("bunko")) {
            return BookFormat.POCKET;
        }
        if (l.contains("grand format") || l.contains("kanzenban")) {
            return BookFormat.LARGE;
        }
        return null;
    }

    private static String isbn13(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        return digits.length() == 13 ? digits : null;
    }

    private static Map<String, JsonNode> byId(JsonNode array) {
        Map<String, JsonNode> map = new HashMap<>();
        for (JsonNode node : array) {
            String id = node.path("id").asText(null);
            if (id != null) {
                map.put(id, node);
            }
        }
        return map;
    }

    /** {@code key} → the earliest day of its rows' {@code created_at}; a key without one maps to null. */
    private static Map<String, LocalDate> datesByKey(JsonNode array, String key) {
        Map<String, LocalDate> map = new HashMap<>();
        for (JsonNode node : array) {
            String id = node.path(key).asText(null);
            if (id == null) {
                continue;
            }
            LocalDate date = date(text(node, "created_at"));
            LocalDate known = map.get(id);
            map.put(id, known == null || (date != null && date.isBefore(known)) ? date : known);
        }
        return map;
    }

    private static LocalDate date(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw).toLocalDate();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            return null;
        }
        return value.asText().trim();
    }
}
