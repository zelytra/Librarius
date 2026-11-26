package zelytra.librarius.imports;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.LibraryStatus;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Booknode's reading history, read off the markup of a real page (#651). */
class BooknodeHistoryParserTest {

    private Document fixture(String name) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
            return Jsoup.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void tellsTheHistoryFromTheLibraryTable() throws Exception {
        assertTrue(BooknodeHistoryParser.isHistory(fixture("booknode-historique.html")));
        assertFalse(BooknodeHistoryParser.isHistory(fixture("booknode-biblio.html")));
    }

    @Test
    void datesEachTitleWithTheMonthItWasRead() throws Exception {
        Map<String, ImportedBook> books = BooknodeHistoryParser.parse(fixture("booknode-historique.html")).stream()
                .collect(Collectors.toMap(ImportedBook::title, Function.identity()));

        assertEquals(3, books.size());
        ImportedBook heritiers = books.get("Les Héritiers, Tome 1 : Le Pacte");
        assertEquals("Laura W.", heritiers.author());
        assertEquals(LibraryStatus.READ, heritiers.status());
        // The month is all Booknode says: the first of it, flagged as a month.
        assertEquals(LocalDate.of(2026, 11, 1), heritiers.readAt());
        assertTrue(heritiers.readMonthOnly());
        assertEquals("platine", heritiers.rank());
        assertEquals("https://cdn1.booknode.com/book_cover/1/heritiers.jpg", heritiers.coverUrl());
        // History dates the library; it never adds a title to it.
        assertTrue(heritiers.datesOnly());

        assertEquals(LocalDate.of(2019, 8, 1), books.get("Ancien Roman").readAt());
    }

    /** A title read twice is finished on its latest reading, whatever order the page lists them in. */
    @Test
    void keepsTheLatestReadingOfATitleReadTwice() throws Exception {
        List<ImportedBook> again = BooknodeHistoryParser.parse(fixture("booknode-historique.html")).stream()
                .filter(b -> b.title().equals("Le Vieil Ami")).toList();

        assertEquals(1, again.size());
        assertEquals(LocalDate.of(2026, 11, 1), again.get(0).readAt());
    }
}
