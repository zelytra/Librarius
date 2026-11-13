package zelytra.librarius.achievement;

import org.junit.jupiter.api.Test;
import zelytra.librarius.achievement.Achievement.Metric;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rules of #287 that need no database: tiers, the level curve, streaks, the XP sum. */
class AchievementRulesTest {

    @Test
    void aTierIsReachedAtItsThreshold() {
        assertEquals(0, Achievement.BOOKWORM.tierReached(9));
        assertEquals(1, Achievement.BOOKWORM.tierReached(10));
        assertEquals(2, Achievement.BOOKWORM.tierReached(99));
        assertEquals(4, Achievement.BOOKWORM.tierReached(10_000));
    }

    @Test
    void theLevelCurveSlowsDown() {
        assertEquals(1, Levels.levelOf(0));
        assertEquals(1, Levels.levelOf(49));
        assertEquals(2, Levels.levelOf(50));
        assertEquals(5, Levels.levelOf(400));
        assertEquals(10, Levels.levelOf(1_350));
        for (int level = 1; level < 50; level++) {
            assertTrue(Levels.xpForLevel(level + 1) - Levels.xpForLevel(level)
                    >= Levels.xpForLevel(level) - Levels.xpForLevel(Math.max(1, level - 1)));
        }
    }

    @Test
    void theStreakIsTheLongestRunOfConsecutiveWeeks() {
        LocalDate monday = LocalDate.of(2026, 1, 5);
        List<LocalDate> weeks = List.of(monday, monday.plusWeeks(1), monday.plusWeeks(2),
                monday.plusWeeks(5), monday.plusWeeks(6));
        assertEquals(3, AchievementMetrics.longestRun(weeks));
        assertEquals(0, AchievementMetrics.longestRun(List.of()));
    }

    @Test
    void xpIsTheUnlockedTiersPlusTenPerBookRead() {
        Map<Metric, Long> values = new EnumMap<>(Metric.class);
        values.put(Metric.BOOKS_READ, 12L);
        var dto = AchievementService.describe(values, Map.of(
                "FIRST_BOOK#1", new AchievementService.Unlock("FIRST_BOOK", 1, null, null),
                "BOOKWORM#1", new AchievementService.Unlock("BOOKWORM", 1, null, java.time.OffsetDateTime.now())));

        assertEquals(12 * Levels.XP_PER_BOOK + 20 + 25, dto.xp());
        var bookworm = dto.achievements().stream().filter(a -> a.code().equals("BOOKWORM")).findFirst().orElseThrow();
        assertEquals(1, bookworm.tier());
        assertEquals(Long.valueOf(50), bookworm.nextThreshold());
        // Only the unlock not seen yet is announced.
        assertEquals(List.of("FIRST_BOOK"), dto.unseen().stream().map(u -> u.code()).toList());
        var omnivore = dto.achievements().stream().filter(a -> a.code().equals("OMNIVORE")).findFirst().orElseThrow();
        assertEquals(0, omnivore.tier());
        assertNull(omnivore.unlockedAt());
    }
}
