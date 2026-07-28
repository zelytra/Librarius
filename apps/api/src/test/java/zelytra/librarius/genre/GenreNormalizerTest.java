package zelytra.librarius.genre;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * States what "the same genre" means.
 *
 * <p>Every rule the fold applies is pinned here, because relaxing one silently splits a
 * genre in two — and the statistics group on the result.
 */
class GenreNormalizerTest {

    // ── Splitting ─────────────────────────────────────────────────────────────

    @Test
    void aListIsSplitOnTheSeparatorsTheProvidersUse() {
        for (String rawList : List.of("Fantasy, Aventure", "Fantasy;Aventure", "Fantasy/Aventure",
                "Fantasy|Aventure", "Fantasy , Aventure", "Fantasy\nAventure")) {
            assertEquals(List.of("fantasy", "aventure"), codesOf(rawList), rawList);
        }
    }

    /** A space separates words, not genres: splitting on it would invent "Science" and "Fiction". */
    @Test
    void aSpaceDoesNotSeparateTwoGenres() {
        assertEquals(List.of("science-fiction"), codesOf("Science fiction"));
    }

    @Test
    void emptyPartsAreDropped() {
        assertEquals(List.of("fantasy"), codesOf("Fantasy,,"));
        assertEquals(List.of("fantasy"), codesOf(",  , Fantasy , "));
        assertEquals(List.of(), codesOf(""));
        assertEquals(List.of(), codesOf("   "));
        assertEquals(List.of(), GenreNormalizer.parts(null));
    }

    // ── Folding ───────────────────────────────────────────────────────────────

    @Test
    void caseAndSurroundingSpaceAreIgnored() {
        assertEquals("fantasy", GenreNormalizer.code("Fantasy"));
        assertEquals("fantasy", GenreNormalizer.code("FANTASY"));
        assertEquals("fantasy", GenreNormalizer.code("  fantasy  "));
    }

    /** The reason the fold exists: three spellings of one genre, one code. */
    @Test
    void punctuationBetweenWordsIsOneHyphenWhateverItWas() {
        assertEquals("science-fiction", GenreNormalizer.code("Science-Fiction"));
        assertEquals("science-fiction", GenreNormalizer.code("Science fiction"));
        assertEquals("science-fiction", GenreNormalizer.code("SCIENCE   FICTION"));
        assertEquals("science-fiction", GenreNormalizer.code("science_fiction"));
    }

    @Test
    void accentsMacronsAndLigaturesFoldOntoAscii() {
        assertEquals("poesie", GenreNormalizer.code("Poésie"));
        assertEquals("poesie", GenreNormalizer.code("POÉSIE"));
        assertEquals("bandes-dessinees", GenreNormalizer.code("Bandes dessinées"));
        assertEquals("shonen", GenreNormalizer.code("Shōnen"));
        assertEquals("seinen", GenreNormalizer.code("Seinen"));
        assertEquals("oeuvre-de-coeur", GenreNormalizer.code("Œuvre de cœur"));
        assertEquals("aegyptus", GenreNormalizer.code("Ægyptus"));
    }

    @Test
    void aWordingWithNothingUsableInItHasNoCode() {
        assertNull(GenreNormalizer.code(""));
        assertNull(GenreNormalizer.code("   "));
        assertNull(GenreNormalizer.code(" - "));
        assertNull(GenreNormalizer.code("!!!"));
        assertNull(GenreNormalizer.code(null));
        // A script the fold does not cover: dropped rather than filed under a code made of
        // hyphens, which every such wording would share.
        assertNull(GenreNormalizer.code("ミステリー"));
    }

    /** {@code genre.code} is 64 characters wide, and a code never ends on a hyphen. */
    @Test
    void anOverlongWordingIsTruncatedToTheWidthOfTheColumn() {
        String code = GenreNormalizer.code("a".repeat(60) + " bbbbb ccccc");

        assertEquals(64, code.length());
        assertEquals("a".repeat(60) + "-bbb", code);

        // The truncation falls on the hyphen, which is then trimmed away.
        assertEquals("a".repeat(63), GenreNormalizer.code("a".repeat(63) + " b"));
    }

    // ── Labels ────────────────────────────────────────────────────────────────

    @Test
    void theLabelKeepsTheWordingAndOnlyEvensOutTheCase() {
        assertEquals("Science fiction", GenreNormalizer.label("SCIENCE FICTION"));
        assertEquals("Poésie", GenreNormalizer.label("  poésie  "));
        assertEquals("Fantasy", GenreNormalizer.label("fantasy"));
    }

    private static List<String> codesOf(String rawList) {
        return GenreNormalizer.parts(rawList).stream()
                .map(GenreNormalizer::code)
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
