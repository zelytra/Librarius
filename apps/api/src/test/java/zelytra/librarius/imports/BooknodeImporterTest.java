package zelytra.librarius.imports;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.LibraryStatus;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Parsing Booknode testé sur une fixture HTML (sans réseau). */
class BooknodeImporterTest {

    private Document fixture() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("booknode-biblio.html")) {
            return Jsoup.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void parsesTitleAuthorCoverAndStatus() throws Exception {
        List<ImportedBook> books = new BooknodeImporter().parse(fixture());

        assertEquals(2, books.size());
        assertEquals("Abysses, Tome 1", books.get(0).title());
        assertEquals("C. S. Quill", books.get(0).author());
        assertEquals(LibraryStatus.READ, books.get(0).status());
        assertEquals("Un jour je t'aimerai", books.get(1).title());
        assertEquals(LibraryStatus.READING, books.get(1).status());
    }
}
