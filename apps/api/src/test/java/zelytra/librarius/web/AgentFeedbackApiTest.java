package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reports as an agent reads and triages them (#518): outside Keycloak, behind the key
 * configured as {@code librarius.agent.api-key} ({@code test-agent-key} in the tests).
 */
@QuarkusTest
class AgentFeedbackApiTest {

    private static final String KEY = "test-agent-key";

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String report(String message, String logs) {
        return given().auth().oauth2(keycloak.getAccessToken("bob")).contentType("application/json")
                .body(Map.of("kind", "BUG", "message", message, "page", "/stats", "userAgent", "Firefox",
                        "logs", logs))
                .when().post("/api/feedback")
                .then().statusCode(201).extract().path("id");
    }

    private JsonPath list(String status) {
        var request = given().header("X-Api-Key", KEY).queryParam("size", 200);
        if (status != null) {
            request.queryParam("status", status);
        }
        return request.when().get("/api/agent/feedback").then().statusCode(200).extract().jsonPath();
    }

    private Map<String, Object> entry(JsonPath page, String id) {
        return page.getMap("items.find { it.id == '" + id + "' }");
    }

    @Test
    void anAgentWithTheKeyReadsTheReportsWithTheirLogs() {
        String message = "Les stats ne chargent pas " + UUID.randomUUID();
        String id = report(message, "GET /api/stats → 500");

        given().header("X-Api-Key", KEY).queryParam("size", 200)
                .when().get("/api/agent/feedback")
                .then().statusCode(200)
                .body("page", is(0))
                .body("size", is(200));
        Map<String, Object> listed = entry(list(null), id);
        assertNotNull(listed, "the report is listed");
        assertEquals(message, listed.get("message"));
        assertEquals("BUG", listed.get("kind"));
        assertEquals("USER", listed.get("source"));
        assertEquals("/stats", listed.get("page"));
        assertEquals("Firefox", listed.get("userAgent"));
        assertTrue(((String) listed.get("details")).contains("GET /api/stats → 500"), "the logs come with it");
        assertNotNull(listed.get("reporterName"));
    }

    @Test
    void readingTheListLeavesANewReportNew() {
        String id = report("Toujours nouveau " + UUID.randomUUID(), "log");
        list(null);
        assertEquals("NEW", entry(list(null), id).get("status"));
    }

    @Test
    void theListIsFilteredByStatusAndPaged() {
        String id = report("Filtré " + UUID.randomUUID(), "log");
        given().header("X-Api-Key", KEY).contentType("application/json").body(Map.of("status", "HANDLED"))
                .when().put("/api/agent/feedback/" + id + "/status").then().statusCode(204);

        assertNotNull(entry(list("HANDLED"), id), "listed among the handled");
        assertEquals(null, entry(list("NEW"), id), "not among the new");

        given().header("X-Api-Key", KEY).queryParam("size", 1)
                .when().get("/api/agent/feedback")
                .then().statusCode(200).body("items.size()", is(1));
        given().header("X-Api-Key", KEY).queryParam("status", "DONE")
                .when().get("/api/agent/feedback").then().statusCode(400);
    }

    @Test
    void anAgentSetsAReportsStatus() {
        String id = report("À trier " + UUID.randomUUID(), "log");
        given().header("X-Api-Key", KEY).contentType("application/json").body(Map.of("status", "IRRELEVANT"))
                .when().put("/api/agent/feedback/" + id + "/status").then().statusCode(204);
        assertEquals("IRRELEVANT", entry(list(null), id).get("status"));

        given().header("X-Api-Key", KEY).contentType("application/json").body(Map.of("status", "DONE"))
                .when().put("/api/agent/feedback/" + id + "/status").then().statusCode(400);
        given().header("X-Api-Key", KEY).contentType("application/json").body(Map.of("status", "HANDLED"))
                .when().put("/api/agent/feedback/" + UUID.randomUUID() + "/status").then().statusCode(404);
    }

    @Test
    void withoutTheRightKeyNothingIsReadNorChanged() {
        String id = report("Secret " + UUID.randomUUID(), "log");
        given().when().get("/api/agent/feedback").then().statusCode(401);
        given().header("X-Api-Key", "wrong-key").when().get("/api/agent/feedback").then().statusCode(401);
        given().header("X-Api-Key", "").when().get("/api/agent/feedback").then().statusCode(401);
        given().contentType("application/json").body(Map.of("status", "HANDLED"))
                .when().put("/api/agent/feedback/" + id + "/status").then().statusCode(401);
        given().header("X-Api-Key", "wrong-key").contentType("application/json").body(Map.of("status", "HANDLED"))
                .when().put("/api/agent/feedback/" + id + "/status").then().statusCode(401);
        assertEquals("NEW", entry(list(null), id).get("status"));
    }

    @Test
    void aMembersSessionIsNotTheKey() {
        given().auth().oauth2(keycloak.getAccessToken("alice"))
                .when().get("/api/agent/feedback").then().statusCode(401);
    }
}
