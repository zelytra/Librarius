package zelytra.librarius.imports;

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
 */
public record ImportedBook(String title, String author, String coverUrl, LibraryStatus status,
        String shelf, Integer rating, LocalDate acquiredAt) {

    /** A row carrying only the four fields every source has; the rest default to none. */
    public ImportedBook(String title, String author, String coverUrl, LibraryStatus status) {
        this(title, author, coverUrl, status, null, null, null);
    }
}
