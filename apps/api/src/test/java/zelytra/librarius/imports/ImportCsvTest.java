package zelytra.librarius.imports;

import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.LibraryStatus;

import java.time.LocalDate;
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
        assertEquals(LibraryStatus.TO_READ, books.get(0).status());
        assertEquals(LibraryStatus.TO_READ, books.get(1).status());
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

    @Test
    void readsTheRatingAndTheDate() {
        String csv = """
                Title,Author,Exclusive Shelf,My Rating,Date Read
                Fourth Wing,Rebecca Yarros,read,4,2024-03-12
                Iron Flame,Rebecca Yarros,read,5,12/04/2024
                """;
        List<ImportedBook> books = ImportService.parseCsv(csv);

        assertEquals(Integer.valueOf(4), books.get(0).rating());
        // A "Date Read" is when the book was finished, not when it was bought.
        assertEquals(LocalDate.of(2024, 3, 12), books.get(0).readAt());
        assertEquals(null, books.get(0).acquiredAt());
        assertEquals(Integer.valueOf(5), books.get(1).rating());
        assertEquals(LocalDate.of(2024, 4, 12), books.get(1).readAt());
    }

    @Test
    void keepsTheDayAddedApartFromTheDayRead() {
        String csv = """
                Title,Author,Exclusive Shelf,Date Read,Date Added,ISBN13,Number of Pages
                Fourth Wing,Rebecca Yarros,read,2024/03/12,2023/12/25,"=""9781649374042""\",498
                Iron Flame,Rebecca Yarros,to-read,,2024/01/02,,
                """;
        List<ImportedBook> books = ImportService.parseCsv(csv);

        assertEquals(LocalDate.of(2024, 3, 12), books.get(0).readAt());
        assertEquals(LocalDate.of(2023, 12, 25), books.get(0).acquiredAt());
        assertEquals("9781649374042", books.get(0).isbn13());
        assertEquals(Integer.valueOf(498), books.get(0).pageCount());
        assertEquals(null, books.get(1).readAt());
        assertEquals(LocalDate.of(2024, 1, 2), books.get(1).acquiredAt());
    }

    @Test
    void readsABareDateAsTheReadingDateOfAReadTitleOnly() {
        String csv = """
                titre;auteur;statut;date
                Dune;Frank Herbert;lu;2023-07-14
                L'Apprenti assassin;Robin Hobb;à lire;2024-01-05
                """;
        List<ImportedBook> books = ImportService.parseCsv(csv);

        assertEquals(LocalDate.of(2023, 7, 14), books.get(0).readAt());
        assertEquals(null, books.get(0).acquiredAt());
        assertEquals(null, books.get(1).readAt());
        assertEquals(LocalDate.of(2024, 1, 5), books.get(1).acquiredAt());
    }

    @Test
    void splitsTheSeriesOutOfAnImportedTitle() {
        assertEquals(new ImportTitle("The Way of Kings", "The Stormlight Archive", 1),
                ImportTitle.parse("The Way of Kings (The Stormlight Archive, #1)"));
        assertEquals(new ImportTitle("One Piece Tome 3", "One Piece", 3),
                ImportTitle.parse("One Piece Tome 3"));
        assertEquals(new ImportTitle("Dune", null, null), ImportTitle.parse("Dune"));
        // A novella numbered between two volumes is no volume.
        assertEquals(new ImportTitle("Edgedancer (The Stormlight Archive, #2.5)", null, null),
                ImportTitle.parse("Edgedancer (The Stormlight Archive, #2.5)"));
    }
}
