package zelytra.librarius.export;

import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.domain.ReadingProgress;
import zelytra.librarius.web.ApiDtos.ExportCollectionItemDto;
import zelytra.librarius.web.ApiDtos.ExportDto;
import zelytra.librarius.web.ApiDtos.ExportProgressDto;
import zelytra.librarius.web.ApiDtos.ExportWishDto;
import zelytra.librarius.web.ApiDtos.ManualBookDto;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The flat book list, in the vocabulary the other reading trackers use.
 *
 * <p>This is the half of the export that exists so a user can <em>leave</em>. It is
 * therefore shaped by two constraints that pull in opposite directions, and both are
 * deliberate:
 *
 * <ul>
 *   <li><b>It has to open in a spreadsheet.</b> UTF-8 <em>with a byte-order mark</em>, so
 *       that Excel shows «Émile» rather than «Ã‰mile»; a semicolon separator, which is what
 *       Excel splits on under a French locale — the only locale this application has — and
 *       what this API's own CSV importer already prefers; CRLF line endings and RFC 4180
 *       quoting, so a title holding a semicolon, a quote or a newline stays in its cell.</li>
 *   <li><b>Its columns have to mean something elsewhere.</b> The names are Goodreads', which
 *       Booknode, StoryGraph and most importers already know how to map: {@code Title},
 *       {@code Author}, {@code ISBN13}, {@code My Rating}, {@code Exclusive Shelf}. What has
 *       no counterpart there is prefixed {@code Librarius } rather than squeezed into a
 *       column that means something else — a receiving tool ignores the columns it does not
 *       know, and a human reading the file is not misled.</li>
 * </ul>
 *
 * <p>Goals and categories are not here, and cannot be: they are not properties of a book, and
 * a flat file that changes shape halfway down stops being openable in a spreadsheet, which
 * was the point. They live in the JSON export, which is also the only one that round-trips.
 */
final class ExportCsv {

    private static final char SEPARATOR = ';';
    private static final String EOL = "\r\n";
    /** U+FEFF, spelled as an escape so that no editor or tool silently eats it. */
    private static final char BOM = '\uFEFF';

    private static final List<String> HEADER = List.of(
            "Title", "Author", "ISBN13", "Publisher", "Number of Pages",
            "Original Publication Year", "My Rating", "My Review", "Exclusive Shelf",
            "Bookshelves", "Date Added", "Date Read", "Private Notes",
            "Librarius Kind", "Librarius Series", "Librarius Volume", "Librarius Rank",
            "Librarius Priority", "Librarius Estimated Price", "Librarius Progress Percent",
            "Librarius Current Page");

    private ExportCsv() {
    }

    static byte[] write(ExportDto document) {
        StringBuilder out = new StringBuilder();
        out.append(BOM);
        row(out, HEADER);
        document.collection().forEach(item -> row(out, collectionRow(item)));
        document.wishlist().forEach(wish -> row(out, wishRow(wish)));
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static List<String> collectionRow(ExportCollectionItemDto item) {
        ManualBookDto book = item.book();
        ExportProgressDto progress = item.progress();
        return List.of(
                text(book.title()),
                text(book.authors()),
                text(book.isbn13()),
                text(book.publisher()),
                text(book.pageCount()),
                text(book.originalYear()),
                text(item.rating()),
                text(item.review()),
                shelf(item.status()),
                "collection",
                text(item.acquiredAt()),
                text(progress != null ? progress.finishedAt() : null),
                "",
                text(book.kind()),
                text(book.seriesTitle()),
                text(book.volumeNumber()),
                text(item.rankCode()),
                "",
                "",
                // Derived when the reader entered a page rather than a percentage: a
                // receiving tool has no way of working it out from a page number alone.
                text(percent(progress, book.pageCount())),
                text(progress != null ? progress.currentPage() : null));
    }

    private static Integer percent(ExportProgressDto progress, Integer pageCount) {
        if (progress == null) {
            return null;
        }
        return progress.percent() != null ? progress.percent()
                : ReadingProgress.percentOf(progress.currentPage(), pageCount);
    }

    private static List<String> wishRow(ExportWishDto wish) {
        ManualBookDto book = wish.book();
        return List.of(
                text(book.title()),
                text(book.authors()),
                text(book.isbn13()),
                text(book.publisher()),
                text(book.pageCount()),
                text(book.originalYear()),
                "",
                "",
                // A wish is a book the user does not own yet, which is exactly what
                // Goodreads calls `to-read`.
                "to-read",
                "wishlist",
                "",
                "",
                text(wish.note()),
                text(book.kind()),
                text(book.seriesTitle()),
                text(book.volumeNumber()),
                "",
                text(wish.priority()),
                text(wish.estimatedPrice()),
                "",
                "");
    }

    /**
     * Reading status in Goodreads' vocabulary. {@code OWNED} means owned but not started,
     * which has no Goodreads equivalent — {@code to-read} is the closest, and is what the
     * importers understand.
     */
    private static String shelf(LibraryStatus status) {
        if (status == null) {
            return "to-read";
        }
        return switch (status) {
            case READ -> "read";
            case READING -> "currently-reading";
            case OWNED -> "to-read";
        };
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static void row(StringBuilder out, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.append(SEPARATOR);
            }
            out.append(quote(cells.get(i)));
        }
        out.append(EOL);
    }

    /**
     * RFC 4180 quoting: a cell holding the separator, a quote or a line break is wrapped in
     * quotes, and its own quotes are doubled. Everything else is written bare, which keeps
     * the file readable.
     */
    private static String quote(String cell) {
        boolean needed = cell.indexOf(SEPARATOR) >= 0 || cell.indexOf('"') >= 0
                || cell.indexOf('\n') >= 0 || cell.indexOf('\r') >= 0;
        if (!needed) {
            return cell;
        }
        return '"' + cell.replace("\"", "\"\"") + '"';
    }
}
