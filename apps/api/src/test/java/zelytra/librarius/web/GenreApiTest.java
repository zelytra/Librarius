package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The genres seen through the API: the breakdown of the statistics, the list a filter is
 * built from, and the filter itself.
 *
 * <p>Titles are tagged with a marker of their own so that the assertions hold on a database
 * shared with the rest of the suite, and the two accounts check that a genre nobody else
 * collects stays invisible.
 */
@QuarkusTest
class GenreApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    /** Sets this run's titles apart from those of the other tests. */
    private final String marker = "genres-" + UUID.randomUUID();

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    private String add(String user, String title, String genres) {
        return given().auth().oauth2(token(user)).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "%s", "authors": "%s",
                                    "genres": %s },
                          "status": "OWNED" }
                        """.formatted(title, marker, genres == null ? "null" : "\"" + genres + "\""))
                .when().post("/api/library")
                .then().statusCode(201)
                .extract().path("id");
    }

    /** The caller's genre codes, most frequent first. */
    private List<String> genres(String user) {
        return given().auth().oauth2(token(user))
                .when().get("/api/genres")
                .then().statusCode(200)
                .extract().jsonPath().getList("code", String.class);
    }

    /** Identifiers of the caller's titles carrying that genre. */
    private List<String> filtered(String user, String genre) {
        return given().auth().oauth2(token(user)).queryParam("genre", genre).queryParam("q", marker)
                .when().get("/api/library")
                .then().statusCode(200)
                .extract().jsonPath().getList("items.id", String.class);
    }

    // ── Breakdown ─────────────────────────────────────────────────────────────

    /**
     * The bug this migration closes: "Fantasy, Aventure" used to be a genre of its own, so a
     * user owning it alongside a "Fantasy" had two fantasy-ish entries counting one each.
     */
    @Test
    void aTitleTaggedWithSeveralGenresCountsTowardsEachOfThem() {
        add("alice", marker + " two genres", "Fantasy, Aventure");
        add("alice", marker + " one genre", "Fantasy");

        assertEquals(2, countIn("alice", "fantasy"), "titles counted as fantasy");
        assertEquals(1, countIn("alice", "aventure"), "titles counted as aventure");
        // And the free-text value is not a genre of its own any more.
        assertEquals(0, countIn("alice", "fantasy-aventure"), "the raw value as a genre");
    }

    /** Spelling, case and language stop splitting a genre in two. */
    @Test
    void wordingsOfTheSameGenreAreCountedTogether() {
        add("alice", marker + " sf 1", "Science-Fiction");
        add("alice", marker + " sf 2", "science fiction");
        add("alice", marker + " sf 3", "SCIENCE FICTION");
        add("alice", marker + " ya", "Juvenile fiction");
        add("alice", marker + " shonen", "Shounen");

        assertEquals(3, countIn("alice", "science-fiction"), "three spellings, one genre");
        assertEquals(1, countIn("alice", "jeunesse"), "Juvenile fiction is Jeunesse");
        assertEquals(1, countIn("alice", "shonen"), "Shounen is Shonen");
    }

    /** A blank value names no genre, and must not create one. */
    @Test
    void blankGenresCreateNothing() {
        add("alice", marker + " blank", "   ");
        add("alice", marker + " none", null);

        assertEquals(List.of(), genres("alice").stream().filter(String::isBlank).toList(),
                "no blank genre code");
    }

    // ── The list ──────────────────────────────────────────────────────────────

    @Test
    void theGenreListCarriesTheCodeToFilterOnAndTheLabelToShow() {
        add("alice", marker + " labelled", "poésie");

        given().auth().oauth2(token("alice"))
                .when().get("/api/genres")
                .then().statusCode(200)
                .body("code", hasItem("poesie"))
                // The curated label, not the lower-case wording the entry used.
                .body("find { it.code == 'poesie' }.genre", is("Poésie"));
    }

    /** The statistics answer with the same normalised genres. */
    @Test
    void theStatisticsBreakdownUsesTheSameGenres() {
        add("alice", marker + " stats", "Fantasy, Aventure");

        given().auth().oauth2(token("alice"))
                .when().get("/api/stats")
                .then().statusCode(200)
                .body("byGenre.code", hasItem("fantasy"))
                .body("byGenre.genre", hasItem("Fantasy"));
    }

    // ── The filter ────────────────────────────────────────────────────────────

    @Test
    void theCollectionCanBeNarrowedDownToOneGenre() {
        String both = add("alice", marker + " filter both", "Fantasy, Aventure");
        String onlyFantasy = add("alice", marker + " filter fantasy", "Fantasy");
        String none = add("alice", marker + " filter none", null);

        assertEquals(sorted(List.of(both, onlyFantasy)), sorted(filtered("alice", "fantasy")));
        assertEquals(List.of(both), filtered("alice", "aventure"));
        assertEquals(List.of(), filtered("alice", "fantasy").stream()
                .filter(none::equals).toList(), "an untagged title is filtered out");
    }

    /**
     * A work carrying several of the genres asked for still counts once: the filter goes
     * through a subquery, where a join would return the item once per matching genre and
     * hand back a page shorter than its size.
     */
    @Test
    void aTitleIsReturnedOnceHoweverManyGenresItCarries() {
        String item = add("alice", marker + " many genres",
                "Fantasy, Aventure, Science-fiction, Romance");

        assertEquals(List.of(item), filtered("alice", "fantasy"));
        given().auth().oauth2(token("alice")).queryParam("genre", "fantasy").queryParam("q", marker)
                .when().get("/api/library")
                .then().statusCode(200)
                .body("total", is(1));
    }

    private static List<String> sorted(List<String> ids) {
        return ids.stream().sorted().toList();
    }

    /** The filter takes a code, and folds anything else the client sends the same way. */
    @Test
    void theFilterAcceptsTheWordingAsWellAsTheCode() {
        String item = add("alice", marker + " folded filter", "Science-fiction");

        assertEquals(List.of(item), filtered("alice", "science-fiction"));
        assertEquals(List.of(item), filtered("alice", "Science Fiction"));
        assertEquals(List.of(item), filtered("alice", "SCIENCE-FICTION"));
    }

    /** An unknown genre narrows the collection down to nothing rather than ignoring itself. */
    @Test
    void anUnknownGenreMatchesNothing() {
        add("alice", marker + " unknown filter", "Fantasy");

        assertEquals(List.of(), filtered("alice", "genre-nobody-uses"));
        assertEquals(List.of(), filtered("alice", "???"));
    }

    // ── Isolation ─────────────────────────────────────────────────────────────

    @Test
    void anonymousCallersAreRejected() {
        given().when().get("/api/genres").then().statusCode(401);
    }

    /**
     * The genre list is the caller's own: a genre only Alice collects must not show up for
     * Bob, and Bob filtering on it must not reach her titles.
     */
    @Test
    void theGenreListAndTheFilterAreScopedToTheCaller() {
        String aliceItem = add("alice", marker + " private genre", "Steampunk uchronique");

        given().auth().oauth2(token("bob"))
                .when().get("/api/genres")
                .then().statusCode(200)
                .body("code", not(hasItem("steampunk-uchronique")));

        assertEquals(List.of(), filtered("bob", "steampunk-uchronique"));
        assertEquals(List.of(aliceItem), filtered("alice", "steampunk-uchronique"));
    }

    /** Number of the caller's titles of this run carrying that genre. */
    private int countIn(String user, String genre) {
        return filtered(user, genre).size();
    }
}
