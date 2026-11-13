package zelytra.librarius.achievement;

/**
 * XP and levels (#287). Every achievement tier grants its XP, and every book read a little more,
 * so that reading itself moves the bar between two badges. The level follows from the total
 * on a curve that slows down: level {@code L} is reached at {@code 50 × (L − 1)^1.5} XP — 50 for
 * level 2, 400 for level 5, 1 350 for level 10, 4 141 for level 20.
 */
public final class Levels {

    /** XP every book read grants, beside the achievements. */
    public static final int XP_PER_BOOK = 10;

    private Levels() {
    }

    /** The total XP level {@code level} starts at. */
    public static long xpForLevel(int level) {
        return level <= 1 ? 0 : Math.round(50 * Math.pow(level - 1, 1.5));
    }

    /** The level a total of XP stands at, 1 at the least. */
    public static int levelOf(long xp) {
        int level = 1;
        while (xpForLevel(level + 1) <= xp) {
            level++;
        }
        return level;
    }
}
