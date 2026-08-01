package zelytra.librarius.author;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * States what "the same person" means — and, just as deliberately, what it does not.
 *
 * <p>Every rule the fold applies is pinned here, because relaxing one splits a bibliography
 * in two and tightening one merges two people's. The second half of the class is the more
 * important one: it asserts the misses, so that nobody reads the feature as doing more
 * disambiguation than it does.
 */
class AuthorNormalizerTest {

    // ── Splitting ─────────────────────────────────────────────────────────────

    @Test
    void aCreditLineIsSplitOnTheSeparatorsTheProvidersUse() {
        // ", " is what OpenLibraryProvider and BnfProvider join names with.
        for (String line : List.of("Isaac Asimov, Robert Silverberg",
                "Isaac Asimov;Robert Silverberg", "Isaac Asimov/Robert Silverberg",
                "Isaac Asimov|Robert Silverberg", "Isaac Asimov & Robert Silverberg",
                "Isaac Asimov\nRobert Silverberg")) {
            assertEquals(List.of("isaac-asimov", "robert-silverberg"), keysOf(line), line);
        }
    }

    /** A space separates words, not people: "Isaac Asimov" is one author. */
    @Test
    void aSpaceDoesNotSeparateTwoAuthors() {
        assertEquals(List.of("isaac-asimov"), keysOf("Isaac Asimov"));
    }

    /** Neither does a full stop, or "J. R. R. Tolkien" would be four authors. */
    @Test
    void aFullStopDoesNotSeparateTwoAuthors() {
        assertEquals(List.of("j-r-r-tolkien"), keysOf("J. R. R. Tolkien"));
    }

    @Test
    void emptyPartsAreDropped() {
        assertEquals(List.of("isaac-asimov"), keysOf("Isaac Asimov,,"));
        assertEquals(List.of("isaac-asimov"), keysOf(",  , Isaac Asimov , "));
        assertEquals(List.of(), keysOf(""));
        assertEquals(List.of(), keysOf("   "));
        assertEquals(List.of(), AuthorNormalizer.parts(null));
        assertNull(AuthorNormalizer.key(null));
        assertNull(AuthorNormalizer.key("   "));
    }

    // ── What folds together ───────────────────────────────────────────────────

    @Test
    void caseAndSurroundingSpaceStopMattering() {
        for (String spelling : List.of("Isaac Asimov", "isaac asimov", "ISAAC ASIMOV",
                "  Isaac Asimov  ", "Isaac  Asimov", "Isaac Asimov.")) {
            assertEquals("isaac-asimov", AuthorNormalizer.key(spelling), spelling);
        }
    }

    /** The catalogues disagree on the spacing of initials; the fold does not care. */
    @Test
    void theSpacingOfInitialsStopsMattering() {
        assertEquals("j-r-r-tolkien", AuthorNormalizer.key("J.R.R. Tolkien"));
        assertEquals("j-r-r-tolkien", AuthorNormalizer.key("J. R. R. Tolkien"));
        assertEquals("j-r-r-tolkien", AuthorNormalizer.key("J R R Tolkien"));
    }

    /**
     * Diacritics fold onto ASCII, so a name typed without them still lands on the person.
     * The Latin Extended-A rows are the reason this table is longer than the genre one.
     */
    @Test
    void diacriticsFoldOntoAscii() {
        assertEquals("herve-le-tellier", AuthorNormalizer.key("Hervé Le Tellier"));
        assertEquals("herve-le-tellier", AuthorNormalizer.key("Herve Le Tellier"));
        assertEquals("stanislaw-lem", AuthorNormalizer.key("Stanisław Lem"));
        assertEquals("stanislaw-lem", AuthorNormalizer.key("Stanislaw Lem"));
        assertEquals("karel-capek", AuthorNormalizer.key("Karel Čapek"));
        assertEquals("andrzej-sapkowski", AuthorNormalizer.key("Andrzej Sapkowski"));
        assertEquals("jo-nesbo", AuthorNormalizer.key("Jo Nesbø"));
        assertEquals("halldor-laxness", AuthorNormalizer.key("Halldór Laxness"));
        assertEquals("bela-hamvas", AuthorNormalizer.key("Béla Hamvas"));
        assertEquals("gyorgy-dragoman", AuthorNormalizer.key("György Dragomán"));
        assertEquals("herta-muller", AuthorNormalizer.key("Herta Müller"));
    }

    /** Ligatures expand to two letters, which a character table cannot do on its own. */
    @Test
    void ligaturesExpand() {
        assertEquals("coeur-de-pirate", AuthorNormalizer.key("Cœur de Pirate"));
        assertEquals(AuthorNormalizer.key("Peter Weiss"), AuthorNormalizer.key("Peter Weiß"));
    }

    // ── What does not fold, and is meant not to ───────────────────────────────

    /**
     * The accepted cost of splitting on the character the providers join with: a name
     * written in library order becomes two authors. Asserted rather than lamented, so that
     * nobody has to discover it from the data.
     */
    @Test
    void anInvertedNameSplitsIntoTwoAuthors() {
        assertEquals(List.of("damasio", "alain"), keysOf("Damasio, Alain"));
    }

    /** Nothing expands an initial: the two spellings are two rows. */
    @Test
    void anInitialIsNotRelatedToTheNameItStandsFor() {
        assertNotEquals(AuthorNormalizer.key("Alain Damasio"), AuthorNormalizer.key("A. Damasio"));
    }

    /** " and " / " et " are words, and a rule cutting on them would cut "Bell and Sons". */
    @Test
    void aConjunctionIsNotASeparator() {
        assertEquals(List.of("neil-gaiman-and-terry-pratchett"),
                keysOf("Neil Gaiman and Terry Pratchett"));
    }

    // ── Names the table cannot fold ───────────────────────────────────────────

    /**
     * A name in a script the table does not cover keeps its own text as the key rather than
     * being dropped the way an unreadable genre is: it is still the only credit that work
     * carries.
     */
    @Test
    void aNameTheFoldCannotReadKeepsItsOwnText() {
        assertEquals("尾田栄一郎", AuthorNormalizer.key("  尾田栄一郎  "));
        assertEquals("Борис Акунин", AuthorNormalizer.key("Борис Акунин"));
    }

    /** And is never confused with a folded key, which always holds a letter or a digit. */
    @Test
    void aVerbatimKeyCannotCollideWithAFoldedOne() {
        assertEquals("---", AuthorNormalizer.key("---"));
        assertEquals("isaac-asimov", AuthorNormalizer.key("--Isaac Asimov--"));
    }

    // ── The spelling that is shown ────────────────────────────────────────────

    /** Unlike a genre, a name is not recased: "Ursula K. Le Guin" is how it is written. */
    @Test
    void theDisplayedNameIsTheSpellingItself() {
        assertEquals("Ursula K. Le Guin", AuthorNormalizer.name("  Ursula K. Le Guin  "));
        assertEquals("ISAAC ASIMOV", AuthorNormalizer.name("ISAAC ASIMOV"));
    }

    // ── Widths ────────────────────────────────────────────────────────────────

    /** Both columns are 512 wide, and the truncation may not leave a trailing hyphen. */
    @Test
    void aNameLongerThanTheColumnIsTruncatedCleanly() {
        // The fold is 513 characters, so the cut lands exactly on the hyphen.
        assertEquals("a".repeat(511), AuthorNormalizer.key("a".repeat(511) + " b"));
        assertEquals(AuthorNormalizer.MAX_LENGTH,
                AuthorNormalizer.name("n".repeat(600)).length());
    }

    private static List<String> keysOf(String creditLine) {
        return AuthorNormalizer.parts(creditLine).stream()
                .map(AuthorNormalizer::key)
                .filter(Objects::nonNull)
                .toList();
    }
}
