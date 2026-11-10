package zelytra.librarius.scan;

import org.junit.jupiter.api.Test;
import zelytra.librarius.catalog.CatalogResult;
import zelytra.librarius.scan.ScanService.Candidate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How a scanned book is recognised among titles imported without an ISBN. */
class ScanMatchTest {

    private static Candidate mine(String title, String authors) {
        return new Candidate(UUID.randomUUID(), null, title, authors, null, null);
    }

    private static CatalogResult catalog(String title, String authors) {
        return new CatalogResult("BOOK", title, authors, null, null, null, null, null, null, null, "bnf", "x");
    }

    @Test
    void readsATitleWordedTheOtherWayRound() {
        // Booknode's wording against the BnF's, Roman numeral included.
        assertTrue(ScanService.closeTitle(mine("Les Misérables, Tome 1 : Fantine", "Victor Hugo"),
                catalog("Fantine : Collection Les Misérables. Tome I", "Hugo, Victor")));
    }

    @Test
    void neverTakesOneVolumeForAnother() {
        assertFalse(ScanService.closeTitle(mine("One Piece, Tome 3", "Eiichirō Oda"),
                catalog("One Piece, Tome 4", "Eiichiro Oda")));
        assertFalse(ScanService.closeTitle(mine("Les Misérables, Tome 2 : Cosette", "Victor Hugo"),
                catalog("Fantine : Collection Les Misérables. Tome I", "Victor Hugo")));
    }

    @Test
    void wantsMostOfTheWordsAndTheSameAuthor() {
        // A bonus novella sharing two words with the novel it follows.
        assertFalse(ScanService.closeTitle(mine("The Empyrean, Tome 1.5 : Bonus Fourth Wing", "Rebecca Yarros"),
                catalog("Fourth Wing", "Rebecca Yarros")));
        assertFalse(ScanService.closeTitle(mine("Les Misérables, Tome 1 : Fantine", "Someone Else"),
                catalog("Fantine : Collection Les Misérables. Tome I", "Victor Hugo")));
    }

    @Test
    void matchesAnExactTitleAcrossPunctuationAndCase() {
        assertTrue(ScanService.sameTitle(mine("Fourth Wing", "Rebecca Yarros"), catalog("Fourth wing", "Rebecca Yarros")));
    }
}
