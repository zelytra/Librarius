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
        List<ImportedBook> all = books();

        // Five rows, one of them a "Pense-bête" — a reminder of a book wanted, not owned: it
        // goes to the wishlist.
        assertEquals(5, all.size());
        assertEquals(1, all.stream().filter(ImportedBook::wished).count());
        List<ImportedBook> books = all.stream().filter(b -> !b.wished()).toList();
        assertEquals(4, books.size());
        assertEquals("La Horde du Contrevent", books.get(0).title());
        assertEquals("Alain Damasio", books.get(0).author());
        assertEquals("One Piece, tome 1 : À l'aube d'une grande aventure", books.get(2).title());
        assertTrue(books.stream().noneMatch(b -> b.title().startsWith("L'Assassin royal")));
    }

    @Test
    void readsTheStatusRatingDateAndCover() throws Exception {
        List<ImportedBook> books = books().stream().filter(b -> !b.wished()).toList();

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

        assertEquals(LibraryStatus.TO_READ, books.get(2).status());
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

    /** Babelio's current markup: one `li.lib-book` per title, the menus' promoted books left out. */
    @Test
    void readsTheCurrentLibraryListAndLeavesTheMenusOut() throws Exception {
        String html;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("babelio-lib-book.html")) {
            html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<ImportedBook> books = BabelioPageParser.parse(Jsoup.parse(html, "https://www.babelio.com/"));

        assertEquals(4, books.size());
        assertTrue(books.stream().noneMatch(b -> b.title().contains("mis en avant")));

        ImportedBook read = books.get(0);
        assertEquals("Premier roman", read.title());
        assertEquals("Une Auteure", read.author());
        assertEquals(LibraryStatus.READ, read.status());
        // The reader's own rating, not the readers' 4,2 average next to it.
        assertEquals(Integer.valueOf(4), read.rating());
        assertEquals(LocalDate.of(2026, 5, 25), read.readAt());
        assertEquals("https://www.babelio.com/couv/premier.jpg", read.coverUrl());

        assertEquals(LibraryStatus.TO_READ, books.get(1).status());
        assertNull(books.get(1).rating());
        assertEquals(LibraryStatus.ABANDONED, books.get(2).status());
        assertEquals(LocalDate.of(2025, 3, 2), books.get(2).readAt());
        // A "Pense-bête" is a wish.
        assertTrue(books.get(3).wished());
    }

    /** The older markup too: a menu's book link is never a row of the library. */
    @Test
    void leavesMenuLinksOutOfTheGenericReading() {
        String html = "<html><body><div class=\"megamenu-body\"><a href=\"/livres/P/1\" class=\"megamenu-hero\">"
                + "<span>Accroche</span><span>Promu</span></a></div>"
                + "<table><tr><td><a href=\"/livres/X/2\">Le mien</a></td><td><a href=\"/auteur/Y/3\">Moi</a></td><td>Lu</td></tr></table>"
                + "</body></html>";
        List<ImportedBook> books = BabelioPageParser.parse(Jsoup.parse(html, "https://www.babelio.com/"));

        assertEquals(1, books.size());
        assertEquals("Le mien", books.get(0).title());
    }
}

