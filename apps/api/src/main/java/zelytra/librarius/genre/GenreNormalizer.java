package zelytra.librarius.genre;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Folds free-text genre wordings into stable codes.
 *
 * <p>Two wordings designate the same genre when they produce the same code: ligatures
 * expanded, accents and macrons folded onto ASCII, lower case, and every run of anything
 * else collapsed into a single hyphen. {@code "Science-Fiction"}, {@code "science fiction"}
 * and {@code "SCIENCE FICTION"} therefore all yield {@code science-fiction}. What differs by
 * more than spelling — another language, a plural, an abbreviation — is related through the
 * {@code genre_alias} table instead.
 *
 * <p>This is a verbatim port of the {@code genre_parts()} and {@code genre_code()} functions
 * created by {@code V6__normalized_genres.sql}, which the backfill of the rows that predate
 * it goes through. {@code GenreNormalizerSqlParityTest} runs both implementations over the
 * same wordings and compares: a divergence would file one genre under two codes depending on
 * whether the work was written before or after the migration.
 */
public final class GenreNormalizer {

    /**
     * Separators a free-text genre list may use. A space is deliberately not one of them:
     * "Science fiction" is a single genre, not two.
     */
    private static final Pattern SEPARATORS = Pattern.compile("[,;/|\r\n]");

    /** Runs of anything a code may not contain, collapsed into a single hyphen. */
    private static final Pattern NON_CODE = Pattern.compile("[^a-z0-9]+");

    /** Width of {@code genre.code} and of {@code genre.label}. */
    static final int MAX_LENGTH = 64;

    /**
     * Accents and macrons, and what they fold onto. The two strings are the arguments of the
     * {@code translate()} the SQL function uses, in the same order: an explicit table rather
     * than Unicode decomposition, so that both implementations cover exactly the same
     * characters and neither depends on the database collation.
     */
    private static final String ACCENTED =
            "ÀÁÂÃÄÅÇÈÉÊËÌÍÎÏÑÒÓÔÕÖØÙÚÛÜÝàáâãäåçèéêëìíîïñòóôõöøùúûüýÿŌōŪū";
    private static final String FOLDED =
            "aaaaaaceeeeiiiinoooooouuuuyaaaaaaceeeeiiiinoooooouuuuyyoouu";

    private GenreNormalizer() {
    }

    /**
     * Splits a free-text genre list into its wordings, empty ones included — they are
     * dropped by {@link #code(String)}, which is where "nothing usable" is decided.
     */
    public static List<String> parts(String rawList) {
        if (rawList == null) {
            return List.of();
        }
        // -1: trailing empty parts are kept, as regexp_split_to_table returns them.
        return Arrays.asList(SEPARATORS.split(rawList, -1));
    }

    /**
     * Folds one wording into its code.
     *
     * @return the code, or {@code null} when nothing usable is left — a blank value,
     *         punctuation only, or a script the fold does not cover
     */
    public static String code(String raw) {
        if (raw == null) {
            return null;
        }
        // Ligatures first: they expand to two characters, which translate() cannot do.
        String expanded = raw.replace("Œ", "oe").replace("œ", "oe")
                .replace("Æ", "ae").replace("æ", "ae");

        StringBuilder folded = new StringBuilder(expanded.length());
        for (int i = 0; i < expanded.length(); i++) {
            char c = expanded.charAt(i);
            int accent = ACCENTED.indexOf(c);
            folded.append(accent >= 0 ? FOLDED.charAt(accent) : c);
        }

        String code = trimHyphens(
                NON_CODE.matcher(folded.toString().toLowerCase(Locale.ROOT)).replaceAll("-"));
        if (code.length() > MAX_LENGTH) {
            // The truncation can uncover a trailing hyphen, hence the second trim.
            code = trimHyphens(code.substring(0, MAX_LENGTH));
        }
        return code.isEmpty() ? null : code;
    }

    /**
     * The label a genre created from this wording gets: the wording itself, cased as "first
     * letter upper, rest lower" so that "FANTASY" and "fantasy" do not read as two different
     * genres in the interface. Purely cosmetic — the code is the identity.
     */
    public static String label(String raw) {
        String lowered = raw.trim().toLowerCase(Locale.ROOT);
        String label = lowered.isEmpty() ? lowered
                : lowered.substring(0, 1).toUpperCase(Locale.ROOT) + lowered.substring(1);
        return label.length() > MAX_LENGTH ? label.substring(0, MAX_LENGTH) : label;
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
