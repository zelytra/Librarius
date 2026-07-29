package zelytra.librarius.domain.repository;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the data migration of {@code V13__author_entities.sql} against real values.
 *
 * <p>Flyway applies it to an empty schema at start-up, where it has nothing to do: the
 * backfill would look green while being unable to split a single credit line. This test seeds
 * {@code work} rows carrying the free-text values the providers actually return — a comma
 * join, a lone name, mixed case, diacritics, blanks, {@code NULL} — then replays the very
 * statements of the migration file and asserts what came out.
 *
 * <p>It replays them twice: the runtime resolves authors the same way for every work written
 * after the migration, so the backfill has to be re-runnable and create nothing the second
 * time round.
 */
@QuarkusTest
class AuthorBackfillTest {

    private static final String MIGRATION = "/db/migration/V13__author_entities.sql";

    /** Everything after this line in the migration is the backfill, and is replayed here. */
    private static final String DATA_MIGRATION_MARKER = "-- ── Data migration";

    /** Statements of the backfill, read once from the migration itself. */
    private static List<String> backfill;

    /** Marker carried by the titles of this test, so it never asserts on someone else's rows. */
    private final String marker = "author-backfill-" + UUID.randomUUID();

    @Inject
    AgroalDataSource dataSource;

    @BeforeAll
    static void readTheMigration() throws IOException {
        try (InputStream stream = AuthorBackfillTest.class.getResourceAsStream(MIGRATION)) {
            assertTrue(stream != null, MIGRATION + " is on the classpath");
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            int start = sql.indexOf(DATA_MIGRATION_MARKER);
            assertTrue(start > 0, "the migration still marks its data section");
            // Comments are stripped before splitting on the statement terminator: the
            // section holds no string literal, so nothing else can carry a `;` or a `--`.
            String section = sql.substring(start).replaceAll("(?m)--.*$", "");

            backfill = Arrays.stream(section.split(";"))
                    .map(String::trim)
                    .filter(statement -> !statement.isEmpty())
                    .toList();
            assertEquals(2, backfill.size(), "statements of the backfill");
        }
    }

    // ── What the backfill produces ────────────────────────────────────────────

    /** The acceptance criterion of the issue: one work, one credit line, two authors. */
    @Test
    void aCreditLineNamingSeveralPeopleBecomesSeveralAuthorsOnTheSameWork() {
        UUID work = seedWork("Isaac Asimov, Robert Silverberg");

        runBackfill();

        assertEquals(List.of("isaac-asimov", "robert-silverberg"), authorsOf(work));
    }

    /**
     * The other acceptance criterion: two works crediting the same person share one row,
     * whatever the spelling. Grouping on the raw value would have created five.
     */
    @Test
    void worksCreditingTheSamePersonShareOneAuthorRow() {
        List<UUID> works = List.of(
                seedWork("Isaac Asimov"),
                seedWork("isaac asimov"),
                seedWork("ISAAC ASIMOV "),
                seedWork("Isaac  Asimov"),
                seedWork("Isaac Asimov."));

        runBackfill();

        for (UUID work : works) {
            assertEquals(List.of("isaac-asimov"), authorsOf(work));
        }
        assertEquals(1, countAuthors("isaac-asimov"), "author rows for that name");
    }

    /** Diacritics are folded, so a name typed without them still lands on the person. */
    @Test
    void spellingsDifferingByADiacriticShareOneAuthorRow() {
        UUID accented = seedWork("Stanisław Lem");
        UUID plain = seedWork("Stanislaw Lem");

        runBackfill();

        assertEquals(List.of("stanislaw-lem"), authorsOf(accented));
        assertEquals(List.of("stanislaw-lem"), authorsOf(plain));
        assertEquals(1, countAuthors("stanislaw-lem"));
    }

    /** Nothing usable in the value, nobody credited — and above all, no empty author row. */
    @Test
    void blankAndMissingCreditLinesCreditNobody() {
        UUID nullValue = seedWork(null);
        UUID blank = seedWork("   ");
        UUID separatorsOnly = seedWork(" , ; & ");

        runBackfill();

        assertEquals(List.of(), authorsOf(nullValue));
        assertEquals(List.of(), authorsOf(blank));
        assertEquals(List.of(), authorsOf(separatorsOnly));
    }

    /** A line naming the same person twice credits them once. */
    @Test
    void aRepeatedNameIsCreditedOnce() {
        UUID work = seedWork("Asimov, asimov, ASIMOV");

        runBackfill();

        assertEquals(List.of("asimov"), authorsOf(work));
    }

    /**
     * The spelling shown is one of those seen and stays the same across a replay — which is
     * the property {@code min()} is there for, the winner itself being the database
     * collation's business. Unlike a genre label it is not recased: neither candidate below
     * is "Ursula k. le guin".
     */
    @Test
    void theDisplayedNameIsAStableSpellingOfThoseSeen() {
        seedWork("Ursula K. Le Guin");
        seedWork("ursula k. le guin");

        runBackfill();
        List<String> afterFirstRun = namesOf("ursula-k-le-guin");

        runBackfill();

        assertEquals(1, afterFirstRun.size(), "author rows for that name");
        assertTrue(List.of("Ursula K. Le Guin", "ursula k. le guin").contains(afterFirstRun.get(0)),
                "one of the spellings seen: " + afterFirstRun);
        assertEquals(afterFirstRun, namesOf("ursula-k-le-guin"), "spelling after a replay");
    }

    /**
     * A name in a script the fold does not cover is kept rather than dropped: it is the only
     * credit that work carries, where an unreadable genre would only have been noise.
     */
    @Test
    void aNameTheFoldCannotReadStillBecomesAnAuthor() {
        UUID work = seedWork("尾田栄一郎");

        runBackfill();

        assertEquals(List.of("尾田栄一郎"), authorsOf(work));
    }

    // ── Re-runnable ───────────────────────────────────────────────────────────

    /**
     * The second run must be a no-op. Flyway would not replay the migration, but the runtime
     * resolves authors the same way for every work written afterwards: a backfill that
     * inserted blindly would race it into duplicates.
     */
    @Test
    void replayingTheBackfillCreatesNothing() {
        UUID work = seedWork("Isaac Asimov, Robert Silverberg & Frederik Pohl");

        runBackfill();
        long authorsAfterFirstRun = countAllAuthors();
        List<String> credited = authorsOf(work);

        runBackfill();

        assertEquals(authorsAfterFirstRun, countAllAuthors(), "authors created by the second run");
        assertEquals(credited, authorsOf(work), "authors credited by the second run");
        assertEquals(List.of("frederik-pohl", "isaac-asimov", "robert-silverberg"), credited);
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    /**
     * Inserts a work carrying that credit line and no author link, exactly as the rows that
     * predate the migration look.
     */
    private UUID seedWork(String authors) {
        UUID id = UUID.randomUUID();
        update("INSERT INTO work (id, kind, title, authors) VALUES (?, 'BOOK', ?, ?)",
                id, marker + " " + id, authors);
        return id;
    }

    private void runBackfill() {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            for (String sql : backfill) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("replaying the backfill", e);
        }
    }

    /** Keys credited on the work, sorted so the assertions do not depend on insertion order. */
    private List<String> authorsOf(UUID work) {
        return query("""
                SELECT a.name_key FROM work_author wa JOIN author a ON a.id = wa.author_id
                WHERE wa.work_id = ? ORDER BY a.name_key
                """, work);
    }

    private List<String> namesOf(String key) {
        return query("SELECT name FROM author WHERE name_key = ?", key);
    }

    private long countAuthors(String key) {
        return Long.parseLong(query("SELECT count(*) FROM author WHERE name_key = ?", key).get(0));
    }

    private long countAllAuthors() {
        return Long.parseLong(query("SELECT count(*) FROM author").get(0));
    }

    private List<String> query(String sql, Object... parameters) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
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

    private void update(String sql, Object... parameters) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(sql, e);
        }
    }

    private static void bind(PreparedStatement statement, Object... parameters)
            throws SQLException {
        for (int i = 0; i < parameters.length; i++) {
            statement.setObject(i + 1, parameters[i]);
        }
    }
}
