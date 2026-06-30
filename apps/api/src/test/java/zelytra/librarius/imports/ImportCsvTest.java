package zelytra.librarius.imports;

import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.LibraryStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Parsing CSV tolérant (en-têtes FR/EN, séparateur , ou ;). */
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
}
