package zelytra.librarius.author;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which provider works an author page keeps: those that actually credit the author. */
class AuthorCreditTest {

    @Test
    void keepsAWorkCreditedToTheAuthorAmongOthers() {
        assertTrue(AuthorService.credits("Frank Herbert", "Frank Herbert"));
        assertTrue(AuthorService.credits("Brian Herbert, Frank Herbert", "Frank Herbert"));
        assertTrue(AuthorService.credits("Kanehito Yamada, Tsukasa Abe", "Tsukasa Abe"));
    }

    @Test
    void dropsAWorkThatOnlySharesAWordWithTheName() {
        // Live BnF answers to an author search on "Frank Herbert".
        assertFalse(AuthorService.credits("F. T. H. Fletcher", "Frank Herbert"));
        assertFalse(AuthorService.credits("Brian Herbert", "Frank Herbert"));
        assertFalse(AuthorService.credits("Frank Thomas Herbert Fletcher", "Frank Herbert"));
        assertFalse(AuthorService.credits("Herbert Frank", "Frank Herbert"));
        assertFalse(AuthorService.credits(null, "Frank Herbert"));
    }

    @Test
    void toleratesRomanisationAccentsAndInitials() {
        assertTrue(AuthorService.credits("Eiichirou Oda", "Eiichiro Oda"));
        assertTrue(AuthorService.credits("Eiichirō Oda", "Eiichiro Oda"));
        assertTrue(AuthorService.credits("J. R. R. Tolkien", "John Ronald Reuel Tolkien"));
        assertTrue(AuthorService.credits("Ursula K. Le Guin", "Ursula K. Le Guin"));
    }

    @Test
    void foldsTheSameTitleWhateverItsEditionCaseOrSubtitle() {
        String key = AuthorService.bibliographyKey("La zone du dehors", null);
        org.junit.jupiter.api.Assertions.assertEquals(key,
                AuthorService.bibliographyKey("La Zone Du Dehors (French Edition)", null));
        org.junit.jupiter.api.Assertions.assertEquals(key,
                AuthorService.bibliographyKey("LA ZONE DU DEHORS : roman", null));
    }

    @Test
    void keysAVolumeOnItsSeriesAndNumber() {
        org.junit.jupiter.api.Assertions.assertEquals(
                AuthorService.bibliographyKey("One Piece Tome 1", 1),
                AuthorService.bibliographyKey("One Piece Tome 1", null));
        org.junit.jupiter.api.Assertions.assertNotEquals(
                AuthorService.bibliographyKey("One Piece Tome 1", 1),
                AuthorService.bibliographyKey("One Piece Tome 2", 2));
    }
}
