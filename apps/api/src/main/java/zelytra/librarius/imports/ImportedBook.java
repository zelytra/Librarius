package zelytra.librarius.imports;

import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.LibraryStatus;

import java.time.LocalDate;

/**
 * Normalized row coming from an import (scraping or file).
 *
 * @param shelf      the source shelf/list the title sat on, or null — its reading status is
 *                   {@link #status}, and a shelf that is not a plain reading state becomes a
 *                   category on the imported item
 * @param rating     the reader's rating out of five, or null when the source carries none
 * @param acquiredAt when the title entered the source library, or null
 * @param readAt     when the reader finished it (or gave up on it), or null
 * @param isbn13     the ISBN-13 the source names, or null
 * @param pageCount  the length the source names, or null
 * @param rank       the built-in rank the source's own tier stands for ({@code or},
 *                   {@code argent}, {@code bronze}), or null — it takes precedence over the shelf
 * @param kind       the kind the source tells (a manga list, a comics one), or null for a book
 * @param wished     a title the reader wants rather than holds: it goes to the wishlist
 */
public record ImportedBook(String title, String author, String coverUrl, LibraryStatus status,
        String shelf, Integer rating, LocalDate acquiredAt, LocalDate readAt, String isbn13,
        Integer pageCount, String rank, Kind kind, boolean wished) {

    /** A row from a source that tells neither a tier, a kind nor a wish. */
    public ImportedBook(String title, String author, String coverUrl, LibraryStatus status,
            String shelf, Integer rating, LocalDate acquiredAt, LocalDate readAt, String isbn13,
            Integer pageCount) {
        this(title, author, coverUrl, status, shelf, rating, acquiredAt, readAt, isbn13, pageCount,
                null, null, false);
    }

    /** A row carrying only the four fields every source has; the rest default to none. */
    public ImportedBook(String title, String author, String coverUrl, LibraryStatus status) {
        this(title, author, coverUrl, status, null, null, null);
    }

    /** A row with no reading date, ISBN or length — what a Booknode row carries. */
    public ImportedBook(String title, String author, String coverUrl, LibraryStatus status,
            String shelf, Integer rating, LocalDate acquiredAt) {
        this(title, author, coverUrl, status, shelf, rating, acquiredAt, null, null, null);
    }
}
