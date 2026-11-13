package zelytra.librarius.achievement;

import java.util.Arrays;
import java.util.Optional;

/**
 * The catalogue of achievements (#287), defined in code: code, family, what it measures, and
 * one threshold and XP reward per tier. A one-shot achievement has a single tier. The names and
 * descriptions are the web app's (`achievements.items.<CODE>` in both locales).
 *
 * <p>Thresholds never shrink once shipped: an unlock is stored, and lowering a bar would hand
 * out a tier nobody earned under the rule it was announced with.
 */
public enum Achievement {

    // ── Reading ──────────────────────────────────────────────────────────────
    FIRST_BOOK(Family.READING, Metric.BOOKS_READ, new long[] {1}, new int[] {20}),
    BOOKWORM(Family.READING, Metric.BOOKS_READ, new long[] {10, 50, 100, 500}, new int[] {25, 75, 150, 300}),
    PAGE_EATER(Family.READING, Metric.PAGES_READ, new long[] {1_000, 10_000, 100_000}, new int[] {25, 100, 250}),
    FINISHER(Family.READING, Metric.SERIES_FINISHED, new long[] {1}, new int[] {75}),
    MARATHON(Family.READING, Metric.ONE_DAY_READS, new long[] {1}, new int[] {50}),
    /** Unlocked by the event itself — an abandoned title read to the end — not by a count. */
    COMEBACK(Family.READING, Metric.EVENT, new long[] {1}, new int[] {50}),

    // ── Consistency ──────────────────────────────────────────────────────────
    ON_A_ROLL(Family.CONSISTENCY, Metric.WEEK_STREAK, new long[] {4, 12, 26}, new int[] {50, 150, 300}),
    GOAL_GETTER(Family.CONSISTENCY, Metric.GOALS_REACHED, new long[] {1}, new int[] {100}),
    AHEAD_OF_THE_CURVE(Family.CONSISTENCY, Metric.GOALS_REACHED_EARLY, new long[] {1}, new int[] {150}),

    // ── Curation & opinion ───────────────────────────────────────────────────
    CRITIC(Family.CURATION, Metric.RATED, new long[] {10, 50}, new int[] {25, 100}),
    WORDSMITH(Family.CURATION, Metric.REVIEWED, new long[] {1}, new int[] {30}),
    LIBRARIAN(Family.CURATION, Metric.TITLES, new long[] {100, 500}, new int[] {50, 200}),
    SORTER(Family.CURATION, Metric.CATEGORIES, new long[] {5}, new int[] {50}),
    EXPLORER(Family.CURATION, Metric.GENRES, new long[] {10}, new int[] {50}),
    POLYGLOT(Family.CURATION, Metric.LANGUAGES, new long[] {3}, new int[] {50}),
    OMNIVORE(Family.CURATION, Metric.KINDS, new long[] {5}, new int[] {100}),

    // ── Discovery & social ───────────────────────────────────────────────────
    SCOUT(Family.DISCOVERY, Metric.FROM_CATALOG, new long[] {10}, new int[] {30}),
    FAN(Family.DISCOVERY, Metric.AUTHORS_FOLLOWED, new long[] {1}, new int[] {20}),
    COMPLETIONIST(Family.DISCOVERY, Metric.AUTHORS_COMPLETE, new long[] {1}, new int[] {150});

    /** How the achievements screen groups them. */
    public enum Family {
        READING, CONSISTENCY, CURATION, DISCOVERY
    }

    /** What an achievement counts; computed per reader by {@link AchievementMetrics}. */
    public enum Metric {
        BOOKS_READ, PAGES_READ, SERIES_FINISHED, ONE_DAY_READS, WEEK_STREAK, GOALS_REACHED,
        GOALS_REACHED_EARLY, RATED, REVIEWED, TITLES, CATEGORIES, GENRES, LANGUAGES, KINDS,
        FROM_CATALOG, AUTHORS_FOLLOWED, AUTHORS_COMPLETE,
        /** Not a count: unlocked where the event happens. */
        EVENT
    }

    public final Family family;
    public final Metric metric;
    private final long[] thresholds;
    private final int[] xp;

    Achievement(Family family, Metric metric, long[] thresholds, int[] xp) {
        this.family = family;
        this.metric = metric;
        this.thresholds = thresholds;
        this.xp = xp;
    }

    /** Number of tiers, 1 for a one-shot achievement. */
    public int tiers() {
        return thresholds.length;
    }

    /** The value tier {@code tier} (1-based) is reached at. */
    public long threshold(int tier) {
        return thresholds[tier - 1];
    }

    /** The XP tier {@code tier} (1-based) grants. */
    public int xp(int tier) {
        return xp[tier - 1];
    }

    /** How many tiers a value reaches, 0 when none. */
    public int tierReached(long value) {
        int tier = 0;
        while (tier < thresholds.length && value >= thresholds[tier]) {
            tier++;
        }
        return tier;
    }

    public static Optional<Achievement> parse(String code) {
        return Arrays.stream(values()).filter(a -> a.name().equals(code)).findFirst();
    }
}
