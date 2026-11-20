package zelytra.librarius.imports;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** #585: what a provider's pseudo may hold. */
class ImportHandleTest {

    @Test
    void acceptsThePseudosOfTheSites() {
        assertTrue(ImportHandle.isValid("lecteur_42"));
        assertTrue(ImportHandle.isValid("Jean.Dupont-2"));
        assertTrue(ImportHandle.isValid("  padded  "));
    }

    @Test
    void refusesAnythingThatSteersTheUrlOrTheLogs() {
        for (String bad : new String[] {null, "", "  ", "a/b", "..", "a?b", "a#b", "a b", "a\nb", "a%2Fb", "x".repeat(65)}) {
            assertFalse(ImportHandle.isValid(bad), bad);
        }
    }
}
