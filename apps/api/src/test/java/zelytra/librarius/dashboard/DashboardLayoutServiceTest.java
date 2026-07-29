package zelytra.librarius.dashboard;

import org.junit.jupiter.api.Test;
import zelytra.librarius.web.ApiDtos.DashboardSectionDto;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DashboardLayoutService#normalize} in isolation — no database, no HTTP, so the
 * "what does a fresh account see" and "what happens to an old layout" questions are answered
 * without the shared Dev Services database the {@code @QuarkusTest} suite depends on.
 */
class DashboardLayoutServiceTest {

    private static List<String> codes(List<DashboardSectionDto> sections) {
        return sections.stream().map(DashboardSectionDto::code).toList();
    }

    /** An account that has never touched the feature: every section, none hidden. */
    @Test
    void nullStoredLayoutBecomesTheFullDefaultOrderAllVisible() {
        List<DashboardSectionDto> normalized = DashboardLayoutService.normalize(null);

        assertEquals(DashboardLayoutService.DEFAULT_ORDER, codes(normalized));
        assertTrue(normalized.stream().noneMatch(DashboardSectionDto::hidden));
    }

    /** An empty list behaves exactly like nothing stored — see the empty-array reset case. */
    @Test
    void emptyStoredLayoutAlsoBecomesTheDefault() {
        assertEquals(DashboardLayoutService.DEFAULT_ORDER, codes(DashboardLayoutService.normalize(List.of())));
    }

    /**
     * The acceptance criterion of #54: a section shipped after the user last saved a
     * layout still shows up, appended after what they chose rather than replacing it.
     */
    @Test
    void aSectionMissingFromAStoredLayoutIsAppendedVisible() {
        List<DashboardSectionDto> stored = List.of(
                new DashboardSectionDto("recentlyRead", true),
                new DashboardSectionDto("goal", false));

        List<DashboardSectionDto> normalized = DashboardLayoutService.normalize(stored);

        assertEquals(List.of("recentlyRead", "goal", "resumeReading", "toRead", "counters", "upcoming"),
                codes(normalized));
        assertTrue(normalized.get(0).hidden(), "the user's own choice to hide it survives");
        assertFalse(normalized.get(2).hidden(), "a section appended for the first time is visible");
    }

    /** A code from a section that no longer exists is dropped, not rejected. */
    @Test
    void anUnknownCodeIsDropped() {
        List<DashboardSectionDto> stored = List.of(new DashboardSectionDto("retiredSection", false));

        assertEquals(DashboardLayoutService.DEFAULT_ORDER, codes(DashboardLayoutService.normalize(stored)));
    }

    /** A duplicate code keeps only its first occurrence. */
    @Test
    void aDuplicateCodeIsKeptOnce() {
        List<DashboardSectionDto> stored = List.of(
                new DashboardSectionDto("goal", true),
                new DashboardSectionDto("goal", false));

        List<DashboardSectionDto> normalized = DashboardLayoutService.normalize(stored);

        assertEquals(1, normalized.stream().filter(s -> s.code().equals("goal")).count());
        assertTrue(normalized.get(0).hidden(), "the first occurrence wins");
    }

    /** A layout that already names every section, reordered, is returned unchanged. */
    @Test
    void aCompleteLayoutIsLeftInTheOrderItWasGiven() {
        List<DashboardSectionDto> stored = List.of(
                new DashboardSectionDto("counters", false),
                new DashboardSectionDto("upcoming", true),
                new DashboardSectionDto("resumeReading", false),
                new DashboardSectionDto("toRead", false),
                new DashboardSectionDto("recentlyRead", false),
                new DashboardSectionDto("goal", false));

        assertEquals(stored, DashboardLayoutService.normalize(stored));
    }
}
