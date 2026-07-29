package zelytra.librarius.author;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Splits a free-text credit line into people, and folds each name into the key two spellings
 * of that person have to share.
 *
 * <p>This is a verbatim port of the {@code author_parts()} and {@code author_key()} functions
 * created by {@code V13__author_entities.sql}, which the backfill of the works that predate
 * it goes through. {@code AuthorNormalizerSqlParityTest} runs both implementations over the
 * same names and compares: a divergence would file one person under two rows depending on
 * whether the work was entered before or after the migration.
 *
 * <p><strong>What the fold does</strong>: case, diacritics and the spacing of punctuation.
 * {@code "ISAAC ASIMOV"}, {@code "Isaac  Asimov"} and {@code "Isaac Asimov."} share the key
 * {@code isaac-asimov}; so do {@code "J.R.R. Tolkien"} and {@code "J. R. R. Tolkien"}, the
 * catalogues disagreeing on the spacing of initials; and so do {@code "Stanisław Lem"} and
 * {@code "Stanislaw Lem"}.
 *
 * <p><strong>What it does not do</strong>, and no amount of folding could: relate
 * {@code "A. Damasio"} to {@code "Alain Damasio"}, keep {@code "Damasio, Alain"} in one piece
 * — the comma is what the providers join names with, so it has to split — or tell two people
 * of the same name apart. There is deliberately no alias table: a curated list of who is who
 * would have to cover the world's writers, and a wrong row there merges two bibliographies
 * rather than mis-filing a tag, which is why {@code genre_alias} has no counterpart here.
 */
public final class AuthorNormalizer {

    /**
     * Separators a credit line may use: those of {@code GenreNormalizer} plus {@code &},
     * which joins a duo far more often than it appears inside one person's name. Neither a
     * space nor a full stop is one — "J. R. R. Tolkien" is a single author.
     */
    private static final Pattern SEPARATORS = Pattern.compile("[,;/|&\r\n]");

    /** Runs of anything a key may not contain, collapsed into a single hyphen. */
    private static final Pattern NON_KEY = Pattern.compile("[^a-z0-9]+");

    /** Width of {@code author.name} and of {@code author.name_key}. */
    static final int MAX_LENGTH = 512;

    /**
     * Diacritics, and what they fold onto. The two strings are the arguments of the
     * {@code translate()} the SQL function uses, in the same order: an explicit table rather
     * than Unicode decomposition, so that both implementations cover exactly the same
     * characters and neither depends on the database collation.
     *
     * <p>Longer than {@code GenreNormalizer}'s on purpose. A genre list is written in French
     * or English; a credit line is written in every language that has a writer, and without
     * the Latin Extended-A rows "Stanisław Lem" and "Stanislaw Lem" would be two authors.
     */
    private static final String ACCENTED =
            "ÀÁÂÃÄÅÇÈÉÊËÌÍÎÏÑÒÓÔÕÖØÙÚÛÜÝàáâãäåçèéêëìíîïñòóôõöøùúûüýÿŌōŪū"
            + "ĀāĂăĄąĆćČčĎďĐđĒēĖėĘęĚěĞğĪīĮįŁłĹĺĽľŃńŇňŐőŔŕŘřŚśŞşŠšŢţŤťŰűŲųŮůŴŵŶŷŸŹźŻżŽž";
    private static final String FOLDED =
            "aaaaaaceeeeiiiinoooooouuuuyaaaaaaceeeeiiiinoooooouuuuyyoouu"
            + "aaaaaaccccddddeeeeeeeeggiiiillllllnnnnoorrrrssssssttttuuuuuuwwyyyzzzzzz";

    private AuthorNormalizer() {
    }

    /**
     * Splits a credit line into its names, empty ones included — they are dropped by
     * {@link #key(String)}, which is where "nothing at all" is decided.
     */
    public static List<String> parts(String creditLine) {
        if (creditLine == null) {
            return List.of();
        }
        // -1: trailing empty parts are kept, as regexp_split_to_table returns them.
        return Arrays.asList(SEPARATORS.split(creditLine, -1));
    }

    /**
     * Folds one name into its key.
     *
     * <p>A name the table cannot fold at all — a script it does not cover — keeps its own
     * trimmed text as the key, verbatim, rather than being dropped the way an unreadable
     * genre is. An unreadable genre is noise; this is somebody's name, and it is the only
     * credit that work carries. It is not lower-cased either: {@code lower()} on a script
     * this table ignores is the database's collation talking, and the fold is deliberately
     * independent of it. Such a key can collide with no folded one, those being
     * {@code [a-z0-9-]} and never empty by construction.
     *
     * @return the key, or {@code null} when the name is blank
     */
    public static String key(String raw) {
        if (raw == null) {
            return null;
        }
        // Ligatures first: they expand to two characters, which translate() cannot do.
        String expanded = raw.replace("Œ", "oe").replace("œ", "oe")
                .replace("Æ", "ae").replace("æ", "ae").replace("ß", "ss");

        StringBuilder folded = new StringBuilder(expanded.length());
        for (int i = 0; i < expanded.length(); i++) {
            char c = expanded.charAt(i);
            int accent = ACCENTED.indexOf(c);
            folded.append(accent >= 0 ? FOLDED.charAt(accent) : c);
        }

        String key = trimHyphens(
                NON_KEY.matcher(folded.toString().toLowerCase(Locale.ROOT)).replaceAll("-"));
        if (key.length() > MAX_LENGTH) {
            // The truncation can uncover a trailing hyphen, hence the second trim.
            key = trimHyphens(key.substring(0, MAX_LENGTH));
        }
        if (!key.isEmpty()) {
            return key;
        }

        String verbatim = trimSpaces(raw);
        if (verbatim.length() > MAX_LENGTH) {
            verbatim = verbatim.substring(0, MAX_LENGTH);
        }
        return verbatim.isEmpty() ? null : verbatim;
    }

    /**
     * The spelling an author created from this name is shown under: the name itself, only
     * trimmed. Unlike a genre it is not recased — "Ursula K. Le Guin" is how it is written,
     * and "Ursula k. le guin" is not. Same value as the {@code min(trim(part))} the backfill
     * settles the spelling with.
     */
    public static String name(String raw) {
        String name = trimSpaces(raw);
        return name.length() > MAX_LENGTH ? name.substring(0, MAX_LENGTH) : name;
    }

    /**
     * PostgreSQL's {@code trim()}, which removes spaces and nothing else — {@code
     * String.trim()} would also strip tabs and control characters, and the two sides of the
     * fold have to agree on the awkward inputs as much as on the ordinary ones.
     */
    private static String trimSpaces(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == ' ') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == ' ') {
            end--;
        }
        return value.substring(start, end);
    }

    private static String trimHyphens(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '-') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '-') {
            end--;
        }
        return value.substring(start, end);
    }
}
