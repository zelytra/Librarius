package zelytra.librarius.imports;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.LibraryStatus;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Booknode parsing tested against an HTML fixture (without network access). */
class BooknodeImporterTest {

    private Document fixture() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("booknode-biblio.html")) {
            return Jsoup.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void parsesEveryRowSkipsTheHeaderAndDropsWants() throws Exception {
        List<ImportedBook> books = new BooknodeImporter().parse(fixture());

        // Six data rows, minus the "Envies" want, which is not a library title: five imported.
        // The `.book-header` row is not a `.book-item`, so it never counted.
        assertEquals(5, books.size());

        assertEquals("Abysses, Tome 1", books.get(0).title());
        assertEquals("C. S. Quill", books.get(0).author());
        assertEquals("https://cdn1.booknode.com/book_cover/5675/mod11/abysses_tome_1.webp",
                books.get(0).coverUrl());
    }

    /**
     * The status is read from the badge's CSS class — Booknode's rating tiers (or, argent,
     * diamant, bronze) and "lu aussi" are all read, which a naive scrape of the badge text
     * missed, filing every read title as merely owned.
     */
    @Test
    void mapsEachBadgeToItsReadingStatus() throws Exception {
        List<ImportedBook> books = new BooknodeImporter().parse(fixture());

        assertEquals(LibraryStatus.READ, books.get(0).status());       // "or" tier
        assertEquals(LibraryStatus.READ, books.get(1).status());       // "lu aussi"
        assertEquals(LibraryStatus.READING, books.get(2).status());    // "en cours de lecture"
        assertEquals(LibraryStatus.ABANDONED, books.get(3).status());  // "abandonné"
        assertEquals(LibraryStatus.OWNED, books.get(4).status());      // "à lire"
    }

    /** A read book's tier becomes a rating out of five; an untiered "lu aussi" carries none. */
    @Test
    void readsTheRatingTierOfAReadBook() throws Exception {
        List<ImportedBook> books = new BooknodeImporter().parse(fixture());

        assertEquals(Integer.valueOf(4), books.get(0).rating());  // gold
        assertNull(books.get(1).rating());                        // read, untiered
        assertNull(books.get(2).rating());                        // reading
    }

    /** The category comes from the custom list (`.group-name`), never from the rating badge. */
    @Test
    void takesTheCategoryFromTheCustomListNotTheBadge() throws Exception {
        List<ImportedBook> books = new BooknodeImporter().parse(fixture());

        assertEquals("romance", books.get(0).shelf());
        assertEquals("jeunesse", books.get(1).shelf());
        assertNull(books.get(2).shelf());  // no custom list
    }

    /** Booknode renders the acquisition date as an ISO timestamp; only the date is kept. */
    @Test
    void readsTheAcquisitionDate() throws Exception {
        List<ImportedBook> books = new BooknodeImporter().parse(fixture());

        assertEquals(LocalDate.of(2024, 3, 12), books.get(0).acquiredAt());
        assertEquals(LocalDate.of(2021, 12, 2), books.get(1).acquiredAt());
    }

    @Test
    void readsTheHighestPageOfThePagination() {
        // The paginated table's first page names the last one (97); the importer fetches them
        // all rather than stopping at page 1 — the whole point of the pagination support.
        Document doc = Jsoup.parse(
                "<ul class='pagination'>"
                        + "<li><a href='/x?page=1'>1</a></li>"
                        + "<li><a href='/x?page=2'>2</a></li>"
                        + "<li><a href='/x?page=97'>97</a></li></ul>");

        assertEquals(97, BooknodeImporter.highestPage(doc));
    }
}
