package zelytra.librarius.catalog;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** #364: a person's name typed in the search matches that person, not an anthology of strangers. */
class AuthorRelevanceTest {

    @Test
    void theWordsHaveToNameOneCreditedPerson() {
        assertTrue(CatalogRanker.oneCreditNames("Frank Herbert", List.of("frank", "herbert")));
        assertTrue(CatalogRanker.oneCreditNames("Brian Herbert, Frank Herbert", List.of("frank", "herbert")));
        // Fifteen names, one "Frank" and one "Herbet" among them: nobody of that name.
        assertFalse(CatalogRanker.oneCreditNames(
                "Frank Andriat, Jean-Baptiste Baronian, Philippe Herbet", List.of("frank", "herbet")));
        assertFalse(CatalogRanker.oneCreditNames(null, List.of("frank")));
    }
}
