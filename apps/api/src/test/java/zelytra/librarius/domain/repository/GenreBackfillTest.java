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
 * Runs the data migration of {@code V6__normalized_genres.sql} against real values.
 *
 * <p>Flyway applies it to an empty schema at start-up, where it has nothing to do: the
 * backfill would look green while being unable to split a single value. This test seeds
 * {@code work} rows carrying the free-text genres the providers actually return — mixed
 * case, accents, several genres in one value, blanks, {@code NULL} — then replays the very
 * statements of the migration file and asserts what came out.
 *
 * <p>It replays them twice: the runtime resolves genres the same way for every work written
 * after the migration, so the backfill has to be re-runnable and create nothing the second
 * time round.
 */
@QuarkusTest
class GenreBackfillTest {

    private static final String MIGRATION = "/db/migration/V6__normalized_genres.sql";

    /** Everything after this line in the migration is the backfill, and is replayed here. */
    private static final String DATA_MIGRATION_MARKER = "-- ── Data migration";

    /** Statements of the backfill, read once from the migration itself. */
    private static List<String> backfill;

    /** Marker carried by the titles of this test, so it never asserts on someone else's rows. */
    private final String marker = "backfill-" + UUID.randomUUID();

    @Inject
    AgroalDataSource dataSource;

    @BeforeAll
    static void readTheMigration() throws IOException {
        try (InputStream stream = GenreBackfillTest.class.getResourceAsStream(MIGRATION)) {
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

    /**
     * The acceptance criterion of the issue: one work, one free-text value, two genres.
     */
    @Test
    void aValueNamingSeveralGenresBecomesSeveralGenresOnTheSameWork() {
        UUID work = seedWork("Fantasy, Aventure");

        runBackfill();

        assertEquals(List.of("aventure", "fantasy"), genresOf(work));
    }

    /**
     * Spelling stops mattering: eight works a provider tagged eight different ways end up on
     * the same three genres. Grouping on the raw value counted eight.
     */
    @Test
    void wordingsOfTheSameGenreCollapseOntoOneCode() {
        List<UUID> works = List.of(
                seedWork("Fantasy"),
                seedWork("fantasy"),
                seedWork("FANTASY "),
                seedWork("Science-Fiction"),
                seedWork("Science fiction"),
                seedWork("SCIENCE   FICTION"),
                seedWork("Poésie"),
                seedWork("POÉSIE"));

        runBackfill();

        assertEquals(List.of("fantasy"), genresOf(works.get(0)));
        assertEquals(List.of("fantasy"), genresOf(works.get(1)));
        assertEquals(List.of("fantasy"), genresOf(works.get(2)));
        assertEquals(List.of("science-fiction"), genresOf(works.get(3)));
        assertEquals(List.of("science-fiction"), genresOf(works.get(4)));
        assertEquals(List.of("science-fiction"), genresOf(works.get(5)));
        assertEquals(List.of("poesie"), genresOf(works.get(6)));
        assertEquals(List.of("poesie"), genresOf(works.get(7)));
    }

    /** The wordings the providers use land on the canonical genre through the alias table. */
    @Test
    void providerWordingsAreMappedOntoTheCanonicalGenre() {
        UUID openLibrary = seedWork("Juvenile fiction, Detective and mystery stories");
        UUID aniList = seedWork("Shounen, Slice of Life, Psychological");
        UUID french = seedWork("Polar; Bandes dessinées");

        runBackfill();

        assertEquals(List.of("jeunesse", "policier"), genresOf(openLibrary));
        assertEquals(List.of("psychologique", "shonen", "tranche-de-vie"), genresOf(aniList));
        assertEquals(List.of("bande-dessinee", "policier"), genresOf(french));
    }

    /** Nothing usable in the value, nothing attached — and above all, no empty genre row. */
    @Test
    void blankAndUnusableValuesAttachNothing() {
        UUID nullValue = seedWork(null);
        UUID blank = seedWork("   ");
        UUID separatorsOnly = seedWork(" , ; / ");
        UUID punctuation = seedWork("!!!");

        runBackfill();

        assertEquals(List.of(), genresOf(nullValue));
        assertEquals(List.of(), genresOf(blank));
        assertEquals(List.of(), genresOf(separatorsOnly));
        assertEquals(List.of(), genresOf(punctuation));
    }

    /** A value naming the same genre twice attaches it once. */
    @Test
    void aRepeatedGenreIsAttachedOnce() {
        UUID work = seedWork("Fantasy, fantasy, FANTASY");

        runBackfill();

        assertEquals(List.of("fantasy"), genresOf(work));
    }

    /**
     * A genre nobody seeded is created from the wording it was seen under, cased so that
     * "STEAMPUNK" and "steampunk" do not read as two genres in the interface.
     */
    @Test
    void anUnknownGenreIsCreatedFromTheWordingItWasSeenUnder() {
        seedWork("STEAMPUNK");
        seedWork("steampunk");

        runBackfill();

        assertEquals(List.of("Steampunk"), labelsOf("steampunk"));
    }

    /** A curated label is not overwritten by the wording found in the data. */
    @Test
    void aSeededGenreKeepsItsCuratedLabel() {
        seedWork("SCIENCE FICTION");

        runBackfill();

        assertEquals(List.of("Science-fiction"), labelsOf("science-fiction"));
    }

    // ── Re-runnable ───────────────────────────────────────────────────────────

    /**
     * The second run must be a no-op. Flyway would not replay the migration, but the runtime
     * resolves genres through the same alias table for every work written afterwards: a
     * backfill that inserted blindly would race it into duplicates.
     */
    @Test
    void replayingTheBackfillCreatesNothing() {
        UUID work = seedWork("Fantasy, Aventure, Steampunk noir");

        runBackfill();
        long genresAfterFirstRun = countGenres();
        List<String> attached = genresOf(work);

        runBackfill();

        assertEquals(genresAfterFirstRun, countGenres(), "genres created by the second run");
        assertEquals(attached, genresOf(work), "genres attached by the second run");
        assertEquals(List.of("aventure", "fantasy", "steampunk-noir"), attached);
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    /**
     * Inserts a work carrying that free-text value and no genre link, exactly as the rows
     * that predate the migration look.
     */
    private UUID seedWork(String genres) {
        UUID id = UUID.randomUUID();
        update("INSERT INTO work (id, kind, title, genres) VALUES (?, 'BOOK', ?, ?)",
                id, marker + " " + id, genres);
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

    /** Codes attached to the work, sorted so the assertions do not depend on insertion order. */
    private List<String> genresOf(UUID work) {
        return query("""
                SELECT g.code FROM work_genre wg JOIN genre g ON g.id = wg.genre_id
                WHERE wg.work_id = ? ORDER BY g.code
                """, work);
    }

    private List<String> labelsOf(String code) {
        return query("SELECT label FROM genre WHERE code = ?", code);
    }

    private long countGenres() {
        return Long.parseLong(query("SELECT count(*) FROM genre").get(0));
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
