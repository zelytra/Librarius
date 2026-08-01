package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * The {@code /api/reports} resource (#192): flagging an error in a shared catalog object.
 *
 * <p>The resource is write-only — a report is created and never read back — so the isolation
 * guarantee is structural: there is no endpoint through which one member could discover a
 * report another filed. The tests pin both halves: a report is persisted with the caller's id,
 * and nothing reads it back in any response shape.
 */
@QuarkusTest
class ReportApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    /** Adds a title and returns its item id, from which a work / edition id can be read. */
    private String addBook(String user, String title) {
        return given().auth().oauth2(token(user)).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "%s", "authors": "Report Test",
                                    "publisher": "Report Press", "pageCount": 200 },
                          "status": "OWNED" }
                        """.formatted(title))
                .when().post("/api/library")
                .then().statusCode(201)
                .extract().path("id");
    }

    private String bookField(String user, String itemId, String field) {
        return given().auth().oauth2(token(user))
                .when().get("/api/library/" + itemId)
                .then().statusCode(200)
                .extract().path("book." + field);
    }

    private String workId(String user, String title) {
        return bookField(user, addBook(user, title), "workId");
    }

    // ── Persisting a report ───────────────────────────────────────────────────

    /**
     * The acceptance case: a report on a real work is persisted and echoed back with the
     * status the column defaults to, without the reporter appearing in the response.
     */
    @Test
    void aReportOnAKnownWorkIsPersisted() {
        String work = workId("alice", "Report - persisted " + UUID.randomUUID());

        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("""
                        { "targetType": "WORK", "targetId": "%s",
                          "reason": "WRONG_COVER", "comment": "Mauvaise couverture." }
                        """.formatted(work))
                .when().post("/api/reports")
                .then().statusCode(201)
                .body("id", notNullValue())
                .body("targetType", is("WORK"))
                .body("targetId", is(work))
                .body("reason", is("WRONG_COVER"))
                .body("comment", is("Mauvaise couverture."))
                .body("status", is("OPEN"))
                // The reporter is never in the response: it is the caller, not data to echo.
                .body("$", not(hasKey("reporterId")));
    }

    /** An edition is a valid target too, and the comment is optional. */
    @Test
    void aReportOnAKnownEditionWithoutACommentIsPersisted() {
        String item = addBook("alice", "Report - edition " + UUID.randomUUID());
        String edition = bookField("alice", item, "editionId");

        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("""
                        { "targetType": "EDITION", "targetId": "%s", "reason": "DUPLICATE" }
                        """.formatted(edition))
                .when().post("/api/reports")
                .then().statusCode(201)
                .body("targetType", is("EDITION"))
                .body("reason", is("DUPLICATE"))
                .body("comment", nullValue())
                .body("status", is("OPEN"));
    }

    /** A series the caller owns a volume of is a valid target. */
    @Test
    void aReportOnAKnownSeriesIsPersisted() {
        String seriesTitle = "Report - series " + UUID.randomUUID();
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("""
                        { "book": { "kind": "MANGA", "title": "%s vol. 1", "authors": "Report Test",
                                    "seriesTitle": "%s", "volumeNumber": 1 },
                          "status": "OWNED" }
                        """.formatted(seriesTitle, seriesTitle))
                .when().post("/api/library").then().statusCode(201);
        String series = given().auth().oauth2(token("alice")).when().get("/api/series")
                .then().statusCode(200)
                .extract().path("find { it.title == '" + seriesTitle + "' }.id");

        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("""
                        { "targetType": "SERIES", "targetId": "%s", "reason": "WRONG_INFO" }
                        """.formatted(series))
                .when().post("/api/reports")
                .then().statusCode(201)
                .body("targetType", is("SERIES"));
    }

    // ── Unknown targets and bad input are 400 ─────────────────────────────────

    /** Reporting an unknown target is a 400, not a silent success. */
    @Test
    void reportingAnUnknownTargetIsRejected() {
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("""
                        { "targetType": "WORK", "targetId": "%s", "reason": "OTHER" }
                        """.formatted(UUID.randomUUID()))
                .when().post("/api/reports")
                .then().statusCode(400);
    }

    /**
     * A real object of the wrong kind is still an unknown target: a work id reported as a
     * series does not exist as a series, so it is a 400.
     */
    @Test
    void reportingAWorkIdUnderTheWrongTargetTypeIsRejected() {
        String work = workId("alice", "Report - mismatched " + UUID.randomUUID());

        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("""
                        { "targetType": "SERIES", "targetId": "%s", "reason": "OTHER" }
                        """.formatted(work))
                .when().post("/api/reports")
                .then().statusCode(400);
    }

    /** A missing reason, target type or target id is a validation 400. */
    @Test
    void anIncompleteReportIsRejected() {
        String work = workId("alice", "Report - incomplete " + UUID.randomUUID());

        // No reason.
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"targetType\": \"WORK\", \"targetId\": \"" + work + "\" }")
                .when().post("/api/reports")
                .then().statusCode(400);

        // No target type.
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"targetId\": \"" + work + "\", \"reason\": \"OTHER\" }")
                .when().post("/api/reports")
                .then().statusCode(400);

        // No target id.
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"targetType\": \"WORK\", \"reason\": \"OTHER\" }")
                .when().post("/api/reports")
                .then().statusCode(400);
    }

    // ── Nothing reads a report back ───────────────────────────────────────────

    /**
     * The isolation guarantee, made structural: there is no way to read a report. Alice files
     * one, and neither she nor Bob has any endpoint that returns it — a {@code GET} on the
     * collection is a 405, and no per-id read exists at all.
     */
    @Test
    void thereIsNoEndpointReadingAReportBack() {
        String work = workId("alice", "Report - unreadable " + UUID.randomUUID());
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("""
                        { "targetType": "WORK", "targetId": "%s", "reason": "OTHER",
                          "comment": "Signal privé d'Alice." }
                        """.formatted(work))
                .when().post("/api/reports").then().statusCode(201);

        // The collection is not readable, to Alice or to anybody.
        given().auth().oauth2(token("alice")).when().get("/api/reports")
                .then().statusCode(405);
        given().auth().oauth2(token("bob")).when().get("/api/reports")
                .then().statusCode(405);
    }

    /** The resource is authenticated like every other: no token, no report. */
    @Test
    void anonymousReportsAreRejected() {
        given().contentType("application/json")
                .body("{ \"targetType\": \"WORK\", \"targetId\": \""
                        + UUID.randomUUID() + "\", \"reason\": \"OTHER\" }")
                .when().post("/api/reports")
                .then().statusCode(401);
    }
}
