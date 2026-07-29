package zelytra.librarius.imports;

import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.LibraryStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Lenient CSV parsing (FR/EN headers, "," or ";" separator). */
class ImportCsvTest {

    @Test
    void parsesFrenchSemicolonCsvWithStatus() {
        String csv = """
                Titre;Auteur;Statut
                Fourth Wing;Rebecca Yarros;Lu
                Iron Flame;Rebecca Yarros;En cours
                """;
        List<ImportedBook> books = ImportService.parseCsv(csv);
        assertEquals(2, books.size());
        assertEquals("Fourth Wing", books.get(0).title());
        assertEquals("Rebecca Yarros", books.get(0).author());
        assertEquals(LibraryStatus.READ, books.get(0).status());
        assertEquals(LibraryStatus.READING, books.get(1).status());
    }

    /**
     * The shelf a book sits on <em>before</em> it is opened is called {@code to-read} —
     * everywhere, Goodreads included, and now in this application's own CSV export. It
     * contains the word "read" without meaning it, and used to come back marked as read: an
     * exported library re-imported itself as entirely finished.
     */
    @Test
    void aBookOnTheToReadShelfIsNotImportedAsRead() {
        String csv = """
                Title,Author,Exclusive Shelf
                Ravage,René Barjavel,to-read
                La Horde du Contrevent,Alain Damasio,à lire
                Fourth Wing,Rebecca Yarros,read
                Iron Flame,Rebecca Yarros,currently-reading
                """;
        List<ImportedBook> books = ImportService.parseCsv(csv);
        assertEquals(4, books.size());
        assertEquals(LibraryStatus.OWNED, books.get(0).status());
        assertEquals(LibraryStatus.OWNED, books.get(1).status());
        assertEquals(LibraryStatus.READ, books.get(2).status());
        assertEquals(LibraryStatus.READING, books.get(3).status());
    }

    /**
     * A book given up on comes back as such. Goodreads has no shelf for it, so the wording
     * belongs to whoever exported the file: {@code abandoned} is what this application
     * writes, {@code dnf} what the English-speaking sites settled on, and a French list says
     * "Abandonné". None of them contains "read", so all three used to land on the to-read
     * pile — the same silent downgrade the test above locks down in the other direction.
     */
    @Test
    void aBookGivenUpOnIsImportedAsAbandoned() {
        String csv = """
                Title,Author,Exclusive Shelf
                Ravage,René Barjavel,abandoned
                La Horde du Contrevent,Alain Damasio,Abandonné
                Fourth Wing,Rebecca Yarros,DNF
                Iron Flame,Rebecca Yarros,lecture en cours abandonnée
                """;
        List<ImportedBook> books = ImportService.parseCsv(csv);
        assertEquals(4, books.size());
        for (ImportedBook book : books) {
            assertEquals(LibraryStatus.ABANDONED, book.status(), book.title());
        }
    }
}
