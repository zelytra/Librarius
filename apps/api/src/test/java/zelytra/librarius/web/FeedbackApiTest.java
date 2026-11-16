package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reports (#489, #490): what a member sends from the bug button, the errors the web app and
 * the API record by themselves, and the admin list that reads them all. In the tests alice
 * holds the admin role and bob does not.
 */
@QuarkusTest
class FeedbackApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    /** The admin list's entry whose message is {@code message}, or null. */
    private Map<String, Object> listed(String message) {
        JsonPath page = given().auth().oauth2(token("alice")).queryParam("size", 100)
                .when().get("/api/admin/feedback")
                .then().statusCode(200).extract().jsonPath();
        return page.getMap("items.find { it.message == '" + message + "' }");
    }

    // ── A member's report ─────────────────────────────────────────────────────

    @Test
    void aMembersReportReachesTheAdminsWithWhoSentItAndItsLogs() {
        String message = "Le bouton ne répond pas " + UUID.randomUUID();
        String id = given().auth().oauth2(token("bob")).contentType("application/json")
                .body(Map.of("kind", "BUG", "message", message, "page", "/collection",
                        "appVersion", "1.2.3", "userAgent", "Firefox", "logs", "GET /api/library → 500"))
                .when().post("/api/feedback")
                .then().statusCode(201).extract().path("id");

        Map<String, Object> entry = listed(message);
        assertNotNull(entry, "the report is listed");
        assertEquals("BUG", entry.get("kind"));
        assertEquals("USER", entry.get("source"));
        assertEquals("/collection", entry.get("page"));
        assertNotNull(entry.get("reporterName"));
        assertNotNull(entry.get("createdAt"));

        given().auth().oauth2(token("alice")).when().get("/api/admin/feedback/" + id)
                .then().statusCode(200)
                .body("message", is(message))
                .body("details", containsString("GET /api/library → 500"))
                .body("userAgent", is("Firefox"))
                .body("appVersion", is("1.2.3"));
    }

    @Test
    void aReportNeedsAKindAndAMessage() {
        given().auth().oauth2(token("bob")).contentType("application/json")
                .body(Map.of("kind", "BUG", "message", "  "))
                .when().post("/api/feedback").then().statusCode(400);
        given().auth().oauth2(token("bob")).contentType("application/json")
                .body(Map.of("kind", "ERROR", "message", "Pas un type qu'un membre choisit"))
                .when().post("/api/feedback").then().statusCode(400);
        given().auth().oauth2(token("bob")).contentType("application/json")
                .body(Map.of("message", "Sans type"))
                .when().post("/api/feedback").then().statusCode(400);
    }

    @Test
    void sendingAReportRequiresSigningIn() {
        given().contentType("application/json").body(Map.of("kind", "IDEA", "message", "Anonyme"))
                .when().post("/api/feedback").then().statusCode(401);
        given().contentType("application/json").body(Map.of("message", "Anonyme"))
                .when().post("/api/feedback/errors").then().statusCode(401);
    }

    // ── Admin only ────────────────────────────────────────────────────────────

    @Test
    void onlyAnAdminReadsTheReports() {
        String id = given().auth().oauth2(token("bob")).contentType("application/json")
                .body(Map.of("kind", "IDEA", "message", "Une idée " + UUID.randomUUID()))
                .when().post("/api/feedback").then().statusCode(201).extract().path("id");

        given().auth().oauth2(token("bob")).when().get("/api/admin/feedback").then().statusCode(403);
        given().auth().oauth2(token("bob")).when().get("/api/admin/feedback/" + id).then().statusCode(403);
        given().auth().oauth2(token("bob")).when().get("/api/admin/monitoring").then().statusCode(403);
        given().when().get("/api/admin/feedback").then().statusCode(401);
        given().auth().oauth2(token("alice")).when().get("/api/admin/feedback/" + UUID.randomUUID())
                .then().statusCode(404);
    }

    // ── Errors recorded by themselves (#490) ──────────────────────────────────

    @Test
    void aWebErrorSeenTwiceIsOneEntryCountedTwice() {
        String message = "TypeError: x is undefined " + UUID.randomUUID();
        Map<String, Object> error = Map.of("message", message, "stack", "at render (Detail.tsx:12)\nat run",
                "page", "/detail/1", "appVersion", "1.2.3", "userAgent", "Safari");
        for (int i = 0; i < 2; i++) {
            given().auth().oauth2(token("bob")).contentType("application/json").body(error)
                    .when().post("/api/feedback/errors").then().statusCode(204);
        }

        Map<String, Object> entry = listed(message);
        assertNotNull(entry);
        assertEquals("ERROR", entry.get("kind"));
        assertEquals("WEB", entry.get("source"));
        assertEquals(2, entry.get("occurrences"));
    }

    @Test
    void anUnexpectedServerErrorIsRecordedOnceAndCounted() {
        given().auth().oauth2(token("bob")).when().get("/api/test/boom").then().statusCode(500)
                .body("message", is("Erreur interne du serveur."));
        given().auth().oauth2(token("bob")).when().get("/api/test/boom").then().statusCode(500);

        Map<String, Object> entry = listed("IllegalStateException: " + BoomResource.MESSAGE);
        assertNotNull(entry, "the server error is listed");
        assertEquals("API", entry.get("source"));
        assertEquals("ERROR", entry.get("kind"));
        assertTrue((Integer) entry.get("occurrences") >= 2);
        assertEquals("GET /api/test/boom", entry.get("page"));

        String id = (String) entry.get("id");
        String details = given().auth().oauth2(token("alice")).when().get("/api/admin/feedback/" + id)
                .then().statusCode(200).extract().path("details");
        assertTrue(details.contains("BoomResource"), "the stack trace is kept:\n" + details);
    }

    /** An answer the API meant — a 404, a 400 — is no error to report, and keeps its status. */
    @Test
    void anIntendedErrorAnswerIsNeitherChangedNorRecorded() {
        given().auth().oauth2(token("bob")).when().get("/api/library/" + UUID.randomUUID())
                .then().statusCode(404);
        given().auth().oauth2(token("bob")).contentType("application/json").body("{ \"rating\": 9 }")
                .when().put("/api/library/" + UUID.randomUUID() + "/review").then().statusCode(400);
        assertNull(listed("NotFoundException"));
    }

    // ── Triage status (#495) ──────────────────────────────────────────────────

    private String report(String message) {
        return given().auth().oauth2(token("bob")).contentType("application/json")
                .body(Map.of("kind", "BUG", "message", message))
                .when().post("/api/feedback").then().statusCode(201).extract().path("id");
    }

    private void setStatus(String id, String status, int expected) {
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body(Map.of("status", status))
                .when().put("/api/admin/feedback/" + id + "/status").then().statusCode(expected);
    }

    @Test
    void aNewReportIsNewAndOpeningItMarksItSeen() {
        String message = "À trier " + UUID.randomUUID();
        String id = report(message);
        assertEquals("NEW", listed(message).get("status"));

        given().auth().oauth2(token("alice")).when().get("/api/admin/feedback/" + id)
                .then().statusCode(200).body("status", is("SEEN"));
        assertEquals("SEEN", listed(message).get("status"));
    }

    @Test
    void anAdminMarksAReportHandledOrIrrelevantAndFiltersOnIt() {
        String handled = "Traité " + UUID.randomUUID();
        String irrelevant = "Hors sujet " + UUID.randomUUID();
        setStatus(report(handled), "HANDLED", 204);
        setStatus(report(irrelevant), "IRRELEVANT", 204);

        assertEquals("HANDLED", listed(handled).get("status"));
        assertEquals("IRRELEVANT", listed(irrelevant).get("status"));
        assertNotNull(listed(handled).get("statusChangedAt"));

        java.util.List<String> handledOnes = given().auth().oauth2(token("alice"))
                .queryParam("status", "HANDLED").queryParam("size", 100)
                .when().get("/api/admin/feedback").then().statusCode(200)
                .extract().jsonPath().getList("items.message");
        assertTrue(handledOnes.contains(handled));
        assertTrue(!handledOnes.contains(irrelevant), "the filter keeps only that status");

        given().auth().oauth2(token("alice")).queryParam("status", "NOPE")
                .when().get("/api/admin/feedback").then().statusCode(400);
    }

    @Test
    void settingAStatusIsForAnAdminOnAKnownReportWithAKnownStatus() {
        String id = report("Statut " + UUID.randomUUID());
        setStatus(id, "SOLVED", 400);
        setStatus(UUID.randomUUID().toString(), "HANDLED", 404);
        given().auth().oauth2(token("bob")).contentType("application/json").body(Map.of("status", "HANDLED"))
                .when().put("/api/admin/feedback/" + id + "/status").then().statusCode(403);
        given().auth().oauth2(token("alice")).contentType("application/json").body(Map.of())
                .when().put("/api/admin/feedback/" + id + "/status").then().statusCode(400);
    }

    /** An error marked handled that happens again is not handled: it comes back as new. */
    @Test
    void anErrorThatComesBackAfterBeingHandledIsNewAgain() {
        String message = "RangeError: encore " + UUID.randomUUID();
        Map<String, Object> error = Map.of("message", message, "stack", "at loop (A.tsx:1)", "page", "/");
        given().auth().oauth2(token("bob")).contentType("application/json").body(error)
                .when().post("/api/feedback/errors").then().statusCode(204);
        setStatus((String) listed(message).get("id"), "HANDLED", 204);

        given().auth().oauth2(token("bob")).contentType("application/json").body(error)
                .when().post("/api/feedback/errors").then().statusCode(204);
        assertEquals("NEW", listed(message).get("status"));
    }

    // ── Monitoring (#491) ─────────────────────────────────────────────────────

    /**
     * The monitoring screen's data: reports per day, members active per day and right now, and
     * whether the services the API depends on answer. Activity is recorded off the request, so
     * the test waits for it a little.
     */
    @Test
    void theMonitoringCountsReportsActiveMembersAndServices() throws InterruptedException {
        // A member's first request creates their account; the app always starts with this one.
        given().auth().oauth2(token("alice")).when().get("/api/me").then().statusCode(200);
        given().auth().oauth2(token("bob")).contentType("application/json")
                .body(Map.of("kind", "OTHER", "message", "Pour la supervision " + UUID.randomUUID()))
                .when().post("/api/feedback").then().statusCode(201);

        JsonPath monitoring = null;
        for (int attempt = 0; attempt < 50; attempt++) {
            monitoring = given().auth().oauth2(token("alice")).when().get("/api/admin/monitoring")
                    .then().statusCode(200).extract().jsonPath();
            if (monitoring.getInt("activeNow") >= 2) {
                break;
            }
            Thread.sleep(100);
        }
        assertTrue(monitoring.getInt("activeNow") >= 2, "bob and alice are active right now");

        String today = java.time.LocalDate.now().toString();
        assertEquals(30, monitoring.getList("days").size(), "the last 30 days, today last");
        assertEquals(today, monitoring.getString("days[-1].day"));
        assertTrue(monitoring.getInt("days[-1].reports") >= 1);
        assertTrue(monitoring.getInt("days[-1].activeUsers") >= 2);

        assertEquals(Boolean.TRUE, monitoring.get("services.find { it.name == 'database' }.up"));
        assertEquals(Boolean.TRUE, monitoring.get("services.find { it.name == 'keycloak' }.up"));
    }
}
