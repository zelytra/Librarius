package zelytra.librarius.imports;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.LibraryStatus;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A Babelio "Mes livres" page, as the reader's own browser collected it. */
class BabelioPageParserTest {

    private List<ImportedBook> books() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("babelio-mes-livres.html")) {
            return BabelioPageParser.parse(Jsoup.parse(
                    new String(in.readAllBytes(), StandardCharsets.UTF_8), "https://www.babelio.com/"));
        }
    }

    @Test
    void readsEachBookOnceWithItsAuthorFromTheSiteLinks() throws Exception {
        List<ImportedBook> books = books();

        // Five rows, one of them a "Pense-bête" — a reminder of a book wanted, not owned.
        assertEquals(4, books.size());
        assertEquals("La Horde du Contrevent", books.get(0).title());
        assertEquals("Alain Damasio", books.get(0).author());
        assertEquals("One Piece, tome 1 : À l'aube d'une grande aventure", books.get(2).title());
        assertTrue(books.stream().noneMatch(b -> b.title().startsWith("L'Assassin royal")));
    }

    @Test
    void readsTheStatusRatingDateAndCover() throws Exception {
        List<ImportedBook> books = books();

        ImportedBook horde = books.get(0);
        assertEquals(LibraryStatus.READ, horde.status());
        assertEquals(Integer.valueOf(5), horde.rating());
        assertEquals(LocalDate.of(2024, 3, 12), horde.readAt());
        assertEquals("https://www.babelio.com/couv/CVT_La-Horde-du-Contrevent_6180.jpg", horde.coverUrl());

        ImportedBook dune = books.get(1);
        assertEquals(LibraryStatus.READING, dune.status());
        assertNull(dune.rating());
        // A date on a title being read is not a reading date.
        assertNull(dune.readAt());

        assertEquals(LibraryStatus.OWNED, books.get(2).status());
        ImportedBook proust = books.get(3);
        assertEquals(LibraryStatus.ABANDONED, proust.status());
        assertEquals(Integer.valueOf(2), proust.rating());
    }

    @Test
    void readsABookShownOnlyAsACover() {
        List<ImportedBook> books = BabelioPageParser.parse(Jsoup.parse(
                "<ul><li><a href=\"/livres/X-Titre/1\"><img src=\"c.jpg\" alt=\"Le Titre\"></a>"
                        + " <a href=\"/auteur/Un-Auteur/2\">Un Auteur</a> Lu</li></ul>",
                "https://www.babelio.com/"));

        assertEquals(1, books.size());
        assertEquals("Le Titre", books.get(0).title());
        assertEquals(LibraryStatus.READ, books.get(0).status());
    }
}
