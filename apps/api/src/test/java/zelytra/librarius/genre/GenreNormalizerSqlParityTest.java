package zelytra.librarius.genre;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the two implementations of the fold in step.
 *
 * <p>The normalisation exists twice: as the {@code genre_parts()} / {@code genre_code()}
 * functions of {@code V6__normalized_genres.sql}, which the backfill of the rows written
 * before it goes through, and as {@link GenreNormalizer}, which every row written after it
 * goes through. They have to agree — a divergence would file one genre under two codes
 * depending on when the title was added, which is the very bug this migration fixes.
 *
 * <p>The wordings below are the ones Open Library and AniList actually return, plus the
 * awkward cases: accents, ligatures, macrons, empty parts, a script the fold does not cover,
 * and a wording longer than the column.
 */
@QuarkusTest
class GenreNormalizerSqlParityTest {

    /** Wordings for a single genre. */
    private static final List<String> WORDINGS = List.of(
            "Fantasy", "fantasy", "FANTASY", "  Fantasy  ",
            "Science-Fiction", "Science fiction", "SCIENCE   FICTION", "science_fiction",
            "Poésie", "POÉSIE", "Bandes dessinées", "Littérature jeunesse",
            "Shōnen", "Shounen", "Shoujo", "Seinen",
            "Œuvre de cœur", "Ægyptus", "Cœur",
            "Juvenile fiction", "Children's fiction", "Biography & Autobiography",
            "Comics & Graphic Novels", "Detective and mystery stories", "Slice of Life",
            "Young Adult", "Non-fiction", "Roman", "Polar", "Mystère",
            "", "   ", " - ", "!!!", "ミステリー", "Роман", "42",
            "a".repeat(60) + " bbbbb ccccc", "a".repeat(63) + " b");

    /** Whole values of {@code work.genres}, as a provider or the manual form writes them. */
    private static final List<String> LISTS = List.of(
            "Fantasy, Aventure", "Fantasy;Aventure", "Fantasy/Aventure", "Fantasy|Aventure",
            "Fantasy,, Aventure", "Fantasy\nAventure", "Fantasy\r\nAventure",
            "Science-fiction, Fantasy, Aventure", "  Fantasy  ,  fantasy  ",
            "Juvenile fiction, Adventure and adventurers", "Shounen / Action / Slice of Life",
            "", "   ", " , ", ",");

    @Inject
    AgroalDataSource dataSource;

    @Test
    void javaAndSqlFoldEveryWordingIdentically() {
        for (String wording : WORDINGS) {
            assertEquals(sqlCode(wording), GenreNormalizer.code(wording),
                    () -> "code of <" + wording + ">");
        }
        assertEquals(sqlCode(null), GenreNormalizer.code(null), "code of NULL");
    }

    @Test
    void javaAndSqlReadTheSameGenresOutOfAWholeValue() {
        for (String rawList : LISTS) {
            assertEquals(sqlCodes(rawList), javaCodes(rawList), () -> "genres of <" + rawList + ">");
        }
        // A NULL column yields no genre on either side; SQL reads it as one empty part, Java
        // as no part at all, and an empty part carries no code.
        assertEquals(List.of(), sqlCodes(null));
        assertEquals(List.of(), javaCodes(null));
    }

    // ── The alias table ───────────────────────────────────────────────────────

    /**
     * An alias is looked up with the code of the wording, so it has to be a code itself: an
     * entry spelled "Juvenile fiction" instead of {@code juvenile-fiction} would never match
     * anything, and nothing else would say so.
     */
    @Test
    void everyAliasIsItselfANormalisedCode() {
        List<String> wrong = query("""
                SELECT alias FROM genre_alias WHERE alias IS DISTINCT FROM genre_code(alias)
                """);

        assertTrue(wrong.isEmpty(), "aliases that are not normalised codes: " + wrong);
    }

    /** An alias that is also a genre code would make the resolution depend on lookup order. */
    @Test
    void noAliasShadowsACanonicalGenre() {
        List<String> shadowing = query("""
                SELECT a.alias FROM genre_alias a JOIN genre g ON g.code = a.alias
                """);

        assertTrue(shadowing.isEmpty(), "aliases shadowing a genre: " + shadowing);
    }

    /** The seeded genres are canonical by construction: their code is their own fold. */
    @Test
    void everySeededGenreCodeIsItsOwnFold() {
        List<String> wrong = query("""
                SELECT code FROM genre WHERE code IS DISTINCT FROM genre_code(code)
                """);

        assertTrue(wrong.isEmpty(), "genre codes that are not normalised: " + wrong);
        assertFalse(query("SELECT code FROM genre WHERE code = 'shonen'").isEmpty(),
                "the seed of V6 is applied");
    }

    // ── Calling the SQL side ──────────────────────────────────────────────────

    private String sqlCode(String raw) {
        return single("SELECT genre_code(?)", raw);
    }

    private List<String> sqlCodes(String rawList) {
        return rows("""
                SELECT genre_code(part) FROM genre_parts(?) AS part
                WHERE genre_code(part) IS NOT NULL
                """, rawList);
    }

    private static List<String> javaCodes(String rawList) {
        return GenreNormalizer.parts(rawList).stream()
                .map(GenreNormalizer::code)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<String> query(String sql) {
        return rows(sql);
    }

    private String single(String sql, String parameter) {
        List<String> rows = rows(sql, parameter);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Runs the statement through plain JDBC: a {@code null} genre is part of what is being
     * compared, and a bare {@code setString(1, null)} states its type where an untyped
     * Hibernate parameter would not.
     */
    private List<String> rows(String sql, String... parameters) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setString(i + 1, parameters[i]);
            }
            try (ResultSet results = statement.executeQuery()) {
                List<String> values = new ArrayList<>();
                while (results.next()) {
                    values.add(results.getString(1));
                }
                return values;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(sql, e);
        }
    }
}
