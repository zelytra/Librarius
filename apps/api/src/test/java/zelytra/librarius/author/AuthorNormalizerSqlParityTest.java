package zelytra.librarius.author;

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

/**
 * Keeps the two implementations of the fold in step.
 *
 * <p>The normalisation exists twice: as the {@code author_parts()} / {@code author_key()}
 * functions of {@code V13__author_entities.sql}, which the backfill of the works written
 * before it goes through, and as {@link AuthorNormalizer}, which every work written after it
 * goes through. They have to agree — a divergence would file one person under two rows
 * depending on when the title was added, and split their bibliography in half.
 *
 * <p>It is also the only check that the two copies of the {@code translate()} table are
 * aligned character for character. {@link AuthorNormalizerTest} covers the other half of the
 * question — that the fold is *right* — since two identically misaligned tables would agree
 * with each other perfectly.
 */
@QuarkusTest
class AuthorNormalizerSqlParityTest {

    /** Single names, from the ordinary to the awkward. */
    private static final List<String> NAMES = List.of(
            "Isaac Asimov", "isaac asimov", "ISAAC ASIMOV", "  Isaac Asimov  ",
            "Isaac  Asimov", "Isaac Asimov.", "Asimov",
            "J.R.R. Tolkien", "J. R. R. Tolkien", "J R R Tolkien",
            "Hervé Le Tellier", "Herve Le Tellier", "Amélie Nothomb", "Émile Zola",
            "Stanisław Lem", "Karel Čapek", "Jo Nesbø", "Halldór Laxness", "Herta Müller",
            "György Dragomán", "Miloš Urban", "Ferenc Molnár", "Şafak Elif", "Tomáš Zmeškal",
            "Cœur de Pirate", "Ægidius", "Peter Weiß", "Peter Weiss",
            "O'Brien", "Le Guin, Ursula K.", "Ursula K. Le Guin",
            "尾田栄一郎", "Борис Акунин", "ミステリー",
            "", "   ", " - ", "---", "!!!", "42",
            "a".repeat(510) + " bbbbb", "a".repeat(511) + " b");

    /** Whole values of {@code work.authors}, as a provider or the manual form writes them. */
    private static final List<String> CREDIT_LINES = List.of(
            "Isaac Asimov, Robert Silverberg", "Isaac Asimov;Robert Silverberg",
            "Isaac Asimov/Robert Silverberg", "Isaac Asimov|Robert Silverberg",
            "Neil Gaiman & Terry Pratchett", "Neil Gaiman and Terry Pratchett",
            "Isaac Asimov,, Robert Silverberg", "Isaac Asimov\nRobert Silverberg",
            "Isaac Asimov\r\nRobert Silverberg", "  Isaac Asimov  ,  isaac asimov  ",
            "Damasio, Alain", "Hergé", "Eiichiro Oda, 尾田栄一郎",
            "", "   ", " , ", ",", "&");

    @Inject
    AgroalDataSource dataSource;

    @Test
    void javaAndSqlFoldEveryNameIdentically() {
        for (String name : NAMES) {
            assertEquals(sqlKey(name), AuthorNormalizer.key(name), () -> "key of <" + name + ">");
        }
        assertEquals(sqlKey(null), AuthorNormalizer.key(null), "key of NULL");
    }

    @Test
    void javaAndSqlReadTheSameAuthorsOutOfAWholeCreditLine() {
        for (String line : CREDIT_LINES) {
            assertEquals(sqlKeys(line), javaKeys(line), () -> "authors of <" + line + ">");
        }
        // A NULL column names nobody on either side; SQL reads it as one empty part, Java as
        // no part at all, and an empty part carries no key.
        assertEquals(List.of(), sqlKeys(null));
        assertEquals(List.of(), javaKeys(null));
    }

    /**
     * The backfill settles the displayed spelling with {@code min(trim(part))}; the runtime
     * settles it with {@link AuthorNormalizer#name}. Both have to be the same trim, or the
     * same person reads differently depending on which side created the row.
     */
    @Test
    void javaAndSqlTrimTheDisplayedNameIdentically() {
        for (String name : NAMES) {
            // A blank name creates no author; and `work.authors` is 512 wide, so a part of
            // it cannot be longer than the column the backfill inserts into.
            if (AuthorNormalizer.key(name) == null || name.length() > AuthorNormalizer.MAX_LENGTH) {
                continue;
            }
            assertEquals(single("SELECT trim(?)", name), AuthorNormalizer.name(name),
                    () -> "name of <" + name + ">");
        }
    }

    // ── Calling the SQL side ──────────────────────────────────────────────────

    private String sqlKey(String raw) {
        return single("SELECT author_key(?)", raw);
    }

    private List<String> sqlKeys(String creditLine) {
        return rows("""
                SELECT author_key(part) FROM author_parts(?) AS part
                WHERE author_key(part) IS NOT NULL
                """, creditLine);
    }

    private static List<String> javaKeys(String creditLine) {
        return AuthorNormalizer.parts(creditLine).stream()
                .map(AuthorNormalizer::key)
                .filter(Objects::nonNull)
                .toList();
    }

    private String single(String sql, String parameter) {
        List<String> rows = rows(sql, parameter);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Runs the statement through plain JDBC: a {@code null} name is part of what is being
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
