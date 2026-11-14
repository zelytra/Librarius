package zelytra.librarius.imports;

import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.BookFormat;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.domain.Ownership;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A Mangacollec collection read against a fixture of the site's own document shape (#448). */
class MangacollecImporterTest {

    private static String fixture() throws Exception {
        try (InputStream in = MangacollecImporterTest.class.getClassLoader()
                .getResourceAsStream("mangacollec-collection.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, ImportedBook> byTitle() throws Exception {
        return MangacollecImporter.parse(fixture()).stream()
                .collect(Collectors.toMap(ImportedBook::title, Function.identity()));
    }

    @Test
    void importsEveryVolumeHeldOrReadAndNothingElse() throws Exception {
        // Six volumes in the document; the novel is neither held nor read. One box is held.
        assertEquals(List.of("Monster Intégral Deluxe Tome 1", "Jujutsu Kaisen Tome 1",
                        "Jujutsu Kaisen Tome 2", "Jujutsu Kaisen Tome 3", "L'Amour et la Mort",
                        "Berserk Edition collector Tome 42"),
                MangacollecImporter.parse(fixture()).stream().map(ImportedBook::title).toList());
    }

    @Test
    void aHeldAndReadVolumeIsReadOwnedAndDated() throws Exception {
        ImportedBook jjk1 = byTitle().get("Jujutsu Kaisen Tome 1");
        assertEquals(LibraryStatus.READ, jjk1.status());
        assertEquals(Ownership.OWNED, jjk1.ownership());
        assertEquals(LocalDate.of(2022, 1, 21), jjk1.acquiredAt());
        assertEquals(LocalDate.of(2022, 7, 14), jjk1.readAt());
        assertEquals("9782820332615", jjk1.isbn13());
        assertEquals("https://example.test/jjk-1.jpg", jjk1.coverUrl());
        assertEquals(Kind.MANGA, jjk1.kind());
        // The series and the volume come from the title, as for every other source.
        assertEquals("Jujutsu Kaisen", ImportTitle.parse(jjk1.title()).seriesTitle());
        assertEquals(1, ImportTitle.parse(jjk1.title()).volumeNumber());
    }

    @Test
    void heldUnreadIsToReadAndReadUnheldIsNotOwned() throws Exception {
        Map<String, ImportedBook> books = byTitle();
        ImportedBook jjk2 = books.get("Jujutsu Kaisen Tome 2");
        assertEquals(LibraryStatus.TO_READ, jjk2.status());
        assertEquals(Ownership.OWNED, jjk2.ownership());
        assertNull(jjk2.readAt());

        ImportedBook jjk3 = books.get("Jujutsu Kaisen Tome 3");
        assertEquals(LibraryStatus.READ, jjk3.status());
        assertEquals(Ownership.NOT_OWNED, jjk3.ownership());
        assertNull(jjk3.acquiredAt());
    }

    @Test
    void theEditionNameTellsTheFormatAndABoxIsABoxSet() throws Exception {
        Map<String, ImportedBook> books = byTitle();
        assertEquals(BookFormat.DELUXE, books.get("Monster Intégral Deluxe Tome 1").format());
        assertNull(books.get("Jujutsu Kaisen Tome 1").format());
        assertEquals(BookFormat.BOX_SET, books.get("Berserk Edition collector Tome 42").format());
        assertEquals(BookFormat.HARDCOVER, MangacollecImporter.formatOf("Edition reliée"));
        assertEquals(BookFormat.LARGE, MangacollecImporter.formatOf("Grand format"));
        assertNull(MangacollecImporter.formatOf("Nouvelle édition"));
    }

    @Test
    void refusesADocumentThatIsNoCollection() {
        assertThrows(ImportException.class, () -> MangacollecImporter.parse("{\"pages\":[]}"));
        assertThrows(ImportException.class, () -> MangacollecImporter.parse("not json"));
        assertThrows(ImportException.class, () -> MangacollecImporter.parse(
                "{\"editions\":[],\"volumes\":[],\"possessions\":[]}"));
    }
}
