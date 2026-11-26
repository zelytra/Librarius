package zelytra.librarius.imports;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** #502: a collected Babelio file is read through the library list, and nothing else. */
class CollectedLibraryTest {

    private static String resource(String name) throws Exception {
        try (InputStream in = CollectedLibraryTest.class.getClassLoader().getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * The owner's real file: nine pages of the library, then a readers' selection the collector
     * reached through a link that merely contained « pense » — 100 strangers' books were imported.
     */
    @Test
    void aPageThatIsNotTheLibraryContributesNothing() throws Exception {
        List<ImportedBook> books = new CollectedLibrary().parse("babelio",
                List.of(resource("babelio-lib-book.html"), resource("babelio-selection.html")));

        assertEquals(4, books.size());
        assertTrue(books.stream().noneMatch(b -> b.title().endsWith("promu")));
    }

    @Test
    void aFileWithOnlyPagesThatAreNotTheLibraryImportsNothing() throws Exception {
        String selection = resource("babelio-selection.html");
        // An old-layout library (no `li.lib-book`) is still read; a selection holds no library.
        assertThrows(ImportException.class, () -> new CollectedLibrary().parse("babelio", List.of(selection)));
    }

    /** Babelio's « Pense-bête » status is `lib-status--3`: a wish, whatever the label reads. */
    @Test
    void theWishStatusIsReadFromItsClassToo() {
        String page = """
                <ul><li class="lib-book"><div class="lib-book-main"><h3 class="lib-book-title"><a href="/livres/A-Voulu/1">Voulu</a></h3>
                <p class="lib-book-author"><a href="/auteur/Un-A/2">Un A</a></p></div>
                <div class="lib-book-state"><p class="lib-status-line"><span class="lib-status lib-status--3">Envie</span></p></div></li></ul>""";
        List<ImportedBook> books = new CollectedLibrary().parse("babelio", List.of(page));
        assertEquals(1, books.size());
        assertTrue(books.get(0).wished());
    }

    /** #506: Babelio labels the wishes « Wishlist » now. */
    @Test
    void aRowLabelledWishlistIsAWish() {
        String page = """
                <ul><li class="lib-book"><div class="lib-book-main"><h3 class="lib-book-title"><a href="/livres/A-Voulu/1">Voulu</a></h3></div>
                <div class="lib-book-state"><p class="lib-status-line"><span class="lib-status">Wishlist</span></p></div></li></ul>""";
        assertTrue(new CollectedLibrary().parse("babelio", List.of(page)).get(0).wished());
    }

    /** A library row, as the collector saves Booknode's table: one book per `.book-item`. */
    private static String libraryRow(String title, String author, String badge) {
        return "<div class=\"book-item\"><div class=\"book-name\"><a href=\"https://booknode.com/x\">" + title
                + "</a></div><div class=\"author-name\"><a>" + author + "</a></div>"
                + "<div class=\"list-name\"><div><label class=\"listebadge " + badge + "\">" + badge
                + "</label></div></div><div class=\"group-name\"><span class=\"group-label\"></span></div>"
                + "<div class=\"date-added\"><span class=\"momentjs-format\">2021-12-02 22:51:48</span></div></div>";
    }

    private static CollectedLibrary booknode() {
        CollectedLibrary collected = new CollectedLibrary();
        collected.booknode = new BooknodeImporter();
        return collected;
    }

    /**
     * #651: the history saved with the library dates its rows — the month only — and the titles it
     * lists that the library does not hold are kept to date, never to add.
     */
    @Test
    void theHistoryDatesTheLibraryItIsCollectedWith() throws Exception {
        List<ImportedBook> books = booknode().parse("booknode",
                List.of(libraryRow("Le Vieil Ami", "Jean D.", "or"), resource("booknode-historique.html")));

        ImportedBook dated = books.stream().filter(b -> b.title().equals("Le Vieil Ami")).findFirst().orElseThrow();
        assertEquals(java.time.LocalDate.of(2026, 11, 1), dated.readAt());
        assertTrue(dated.readMonthOnly());
        org.junit.jupiter.api.Assertions.assertFalse(dated.datesOnly());
        // Its acquisition date is still the library's.
        assertEquals(java.time.LocalDate.of(2021, 12, 2), dated.acquiredAt());
        assertEquals(1, books.stream().filter(b -> b.title().equals("Le Vieil Ami")).count());
        assertTrue(books.stream().filter(b -> !b.title().equals("Le Vieil Ami")).allMatch(ImportedBook::datesOnly));
    }

    /** The history collected on its own: rows that date the library already imported. */
    @Test
    void aHistoryOnItsOwnOnlyDates() throws Exception {
        List<ImportedBook> books = booknode().parse("booknode", List.of(resource("booknode-historique.html")));

        assertEquals(3, books.size());
        assertTrue(books.stream().allMatch(ImportedBook::datesOnly));
    }

    /** The history names a book's first author; the library all of them (#651, measured: 81 of 414). */
    @Test
    void theHistoryFindsABookWhoseLibraryRowNamesMoreAuthors() throws Exception {
        List<ImportedBook> books = booknode().parse("booknode",
                List.of(libraryRow("Le Vieil Ami", "Jean D. Marie C.", "or"), resource("booknode-historique.html")));

        ImportedBook dated = books.stream().filter(b -> b.title().equals("Le Vieil Ami")).findFirst().orElseThrow();
        assertEquals(java.time.LocalDate.of(2026, 11, 1), dated.readAt());
        assertEquals(1, books.stream().filter(b -> b.title().equals("Le Vieil Ami")).count());
    }
}
