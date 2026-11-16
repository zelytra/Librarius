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
}
