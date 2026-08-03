package zelytra.librarius.imports;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.LibraryStatus;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Booknode parsing tested against an HTML fixture (without network access). */
class BooknodeImporterTest {

    private Document fixture() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("booknode-biblio.html")) {
            return Jsoup.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void parsesEveryRowAndSkipsTheColumnHeader() throws Exception {
        List<ImportedBook> books = new BooknodeImporter().parse(fixture());

        // Three data rows — the `.book-header` "Nom / Auteur(s) / Liste" row is not a book.
        assertEquals(3, books.size());

        assertEquals("Abysses, Tome 1", books.get(0).title());
        assertEquals("C. S. Quill", books.get(0).author());
        assertEquals("https://cdn1.booknode.com/book_cover/5675/mod11/abysses_tome_1.webp",
                books.get(0).coverUrl());
    }

    @Test
    void mapsEachShelfToAReadingStatus() throws Exception {
        List<ImportedBook> books = new BooknodeImporter().parse(fixture());

        assertEquals(LibraryStatus.READ, books.get(0).status());       // "Lu"
        assertEquals(LibraryStatus.READING, books.get(1).status());    // "En train de lire"
        assertEquals(LibraryStatus.ABANDONED, books.get(2).status());  // "Abandonné"
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
