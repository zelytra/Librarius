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

    /** #653: the page a server fetches carries the year's readings as JSON on its Vue root. */
    @Test
    void readsTheReadingsAPlainFetchOfTheHistoryCarries() throws Exception {
        Document page = fixture("booknode-historique-page.html");

        assertTrue(BooknodeHistoryParser.isHistory(page));
        assertEquals(2026, BooknodeHistoryParser.yearShown(page));
        assertEquals(List.of(2026, 2019), BooknodeHistoryParser.yearsListed(page));
        Map<String, ImportedBook> books = BooknodeHistoryParser.parse(page).stream()
                .collect(Collectors.toMap(ImportedBook::title, Function.identity()));
        assertEquals(2, books.size());
        ImportedBook heritiers = books.get("Les Héritiers, Tome 1 : Le Pacte");
        assertEquals(LocalDate.of(2026, 11, 1), heritiers.readAt());
        assertTrue(heritiers.readMonthOnly());
        assertTrue(heritiers.datesOnly());
        assertEquals("platine", heritiers.rank());
        assertEquals("Laura W.", heritiers.author());
        assertEquals("https://cdn1.booknode.com/book_cover/1/les_heritiers_tome_1.jpg", heritiers.coverUrl());
        // Read in February and again in November: the latest month, and the first author named.
        assertEquals(LocalDate.of(2026, 11, 1), books.get("Le Vieil Ami").readAt());
        assertEquals("Jean D.", books.get("Le Vieil Ami").author());
    }

    /** The JSON keeps a double space the rendered page collapses: "Tome 0 :  Les origines" (measured, #653). */
    @Test
    void collapsesTheSpacesOfATitleTheWayThePageShowsIt() {
        Document page = Jsoup.parse("<div data-vue-app data-year=\"2026\" data-lists='{}' data-table-years='[]' "
                + "data-current-year-details='[{\"month_name\":\"mars\",\"reads\":[{\"list_id\":2,\"book\":"
                + "{\"name\":\"Community, Tome 0 :  Les origines\",\"authors\":[{\"nom\":\"Luna J.\"}]}}]}]'></div>");

        assertEquals("Community, Tome 0 : Les origines", BooknodeHistoryParser.parse(page).get(0).title());
    }

    /**
     * #653: another year comes from the address the page's script asks, /historique/<year>, as
     * JSON — the months of the current page's shape; the tiers are the page's.
     */
    @Test
    void readsAnotherYearFromTheJsonThePageAsksFor() throws Exception {
        String json;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("booknode-historique-2019.json")) {
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Document page = fixture("booknode-historique-page.html");

        List<ImportedBook> books = BooknodeHistoryParser.parseYear(json, 2019, page);

        assertEquals(1, books.size());
        assertEquals("Ancien Roman", books.get(0).title());
        assertEquals("Anne A.", books.get(0).author());
        assertEquals(LocalDate.of(2019, 8, 1), books.get(0).readAt());
        assertEquals(Integer.valueOf(1), books.get(0).rating());
    }
}
