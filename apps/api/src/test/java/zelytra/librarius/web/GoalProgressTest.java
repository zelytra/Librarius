package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import java.time.Year;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The loop the goal gauge is built on: marking a title as read stamps {@code finished_at},
 * and what is stamped inside the current year is what the gauge counts.
 *
 * <p>The database is shared by the whole suite, so every figure here is asserted as a delta
 * around the operation under test rather than in absolute terms.
 */
@QuarkusTest
class GoalProgressTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private final int year = Year.now().getValue();

    private String token() {
        return keycloak.getAccessToken("alice");
    }

    /** Sets the caller's goal for the year in progress. */
    private void setGoal(int target, String unit) {
        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"targetCount\": %d, \"unit\": \"%s\" }".formatted(target, unit))
                .when().put("/api/goals/" + year)
                .then().statusCode(200);
    }

    /** Adds a title and returns its identifier. */
    private String addTitle(String title, Integer volumeNumber, int pageCount) {
        return given().auth().oauth2(token()).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "%s", "authors": "Goal Test",
                                    "volumeNumber": %s, "pageCount": %d },
                          "status": "OWNED" }
                        """.formatted(title, String.valueOf(volumeNumber), pageCount))
                .when().post("/api/library")
                .then().statusCode(201)
                .extract().path("id");
    }

    private void setStatus(String item, String status) {
        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"status\": \"%s\" }".formatted(status))
                .when().put("/api/library/" + item + "/progress")
                .then().statusCode(204);
    }

    private int goalCurrent() {
        return given().auth().oauth2(token())
                .when().get("/api/stats")
                .then().statusCode(200)
                .extract().jsonPath().getInt("goalCurrent");
    }

    // ── The target ────────────────────────────────────────────────────────────

    /** The unit travels with the target: a gauge cannot label itself without it. */
    @Test
    void reportsTheTargetAndItsUnit() {
        setGoal(4200, "PAGES");

        given().auth().oauth2(token())
                .when().get("/api/stats")
                .then().statusCode(200)
                .body("goalTarget", is(4200))
                .body("goalUnit", is("PAGES"));
    }

    /** A year with no goal reports no target and no unit rather than a zero. */
    @Test
    void reportsNoTargetForAUserWithoutAGoal() {
        String bob = keycloak.getAccessToken("bob");
        // Bob only ever sets a goal on a distant year, so the current one is free. Asserted
        // rather than assumed: a suite that started giving him one would otherwise fail
        // below with nothing pointing at the reason.
        assertTrue(given().auth().oauth2(bob).when().get("/api/goals")
                        .then().statusCode(200)
                        .extract().jsonPath().getList("findAll { it.year == " + year + " }").isEmpty(),
                "the fixture user must have no goal for the year in progress");

        given().auth().oauth2(bob)
                .when().get("/api/stats")
                .then().statusCode(200)
                .body("goalTarget", nullValue())
                .body("goalUnit", nullValue());
    }

    // ── Progress ──────────────────────────────────────────────────────────────

    @Test
    void markingATitleAsReadAdvancesTheGoal() {
        setGoal(40, "BOOKS");
        int before = goalCurrent();

        setStatus(addTitle("Goal - one book", null, 321), "READ");

        assertEquals(before + 1, goalCurrent(), "one more title finished this year");
    }

    /** Going back to READING clears the finishing date: the title stops counting. */
    @Test
    void resumingATitleTakesItBackOutOfTheGoal() {
        setGoal(40, "BOOKS");
        String item = addTitle("Goal - resumed", null, 100);

        setStatus(item, "READ");
        int finished = goalCurrent();
        setStatus(item, "READING");

        assertEquals(finished - 1, goalCurrent(), "a title being read again is not a finished one");
    }

    /** Marking the same title as read twice does not count it twice. */
    @Test
    void markingATitleAsReadTwiceCountsItOnce() {
        setGoal(40, "BOOKS");
        String item = addTitle("Goal - marked twice", null, 100);

        setStatus(item, "READ");
        int once = goalCurrent();
        setStatus(item, "READ");

        assertEquals(once, goalCurrent());
    }

    // ── Units ─────────────────────────────────────────────────────────────────

    /**
     * The same reading read three ways: one title, one volume of a run, and its pages. The
     * standalone title counts for the books goal and not for the volumes one.
     */
    @Test
    void countsTheSameReadingInTheUnitOfTheGoal() {
        setGoal(40, "BOOKS");
        int booksBefore = goalCurrent();
        setGoal(40, "VOLUMES");
        int volumesBefore = goalCurrent();
        setGoal(100_000, "PAGES");
        int pagesBefore = goalCurrent();

        setStatus(addTitle("Goal - standalone", null, 300), "READ");
        setStatus(addTitle("Goal - volume seven", 7, 200), "READ");

        setGoal(40, "BOOKS");
        assertEquals(booksBefore + 2, goalCurrent(), "both titles count as books");
        setGoal(40, "VOLUMES");
        assertEquals(volumesBefore + 1, goalCurrent(), "only the one carrying a volume number");
        setGoal(100_000, "PAGES");
        assertEquals(pagesBefore + 500, goalCurrent(), "the pages of both");
    }
}
