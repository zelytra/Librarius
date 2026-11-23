package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static zelytra.librarius.web.SocialTestSupport.token;

/**
 * The admins' view of the reports filed from the book screens (#621) — distinct from the bug
 * reports of {@code /api/admin/feedback}. alice is the admin.
 */
@QuarkusTest
class AdminCatalogReportsApiTest {

    private static String workOf(String user, String title) {
        String item = given().auth().oauth2(token(user)).contentType("application/json")
                .body(Map.of("book", Map.of("kind", "BOOK", "title", title, "authors", "Report Admin"), "status", "TO_READ"))
                .when().post("/api/library").then().statusCode(201).extract().path("id");
        return given().auth().oauth2(token(user)).when().get("/api/library/" + item)
                .then().statusCode(200).extract().path("book.workId");
    }

    private static String report(String user, String work, String comment) {
        return given().auth().oauth2(token(user)).contentType("application/json")
                .body(Map.of("targetType", "WORK", "targetId", work, "reason", "WRONG_INFO", "comment", comment))
                .when().post("/api/reports").then().statusCode(201).extract().path("id");
    }

    private static Map<String, Object> entry(String id, String status) {
        var request = given().auth().oauth2(token("alice")).queryParam("size", 100);
        if (status != null) {
            request.queryParam("status", status);
        }
        return request.when().get("/api/admin/reports").then().statusCode(200).extract().jsonPath()
                .getMap("items.find { it.id == '" + id + "' }");
    }

    private static void setStatus(String user, String id, String status, int expected) {
        given().auth().oauth2(token(user)).contentType("application/json").body(Map.of("status", status))
                .when().put("/api/admin/reports/" + id + "/status").then().statusCode(expected);
    }

    @Test
    void anAdminSeesTheReportWithWhatItTargetsAndWhoFiledIt() {
        String title = "Admin report - listed " + UUID.randomUUID();
        String id = report("bob", workOf("bob", title), "L'auteur est faux.");

        Map<String, Object> entry = entry(id, "OPEN");
        assertNotNull(entry);
        assertEquals("WORK", entry.get("targetType"));
        assertEquals(title, entry.get("targetTitle"));
        assertEquals("Report Admin", entry.get("targetDetail"));
        assertEquals("WRONG_INFO", entry.get("reason"));
        assertEquals("L'auteur est faux.", entry.get("comment"));
        assertEquals("OPEN", entry.get("status"));
        assertNotNull(((Map<?, ?>) entry.get("reporter")).get("displayName"));
        assertNotNull(entry.get("createdAt"));
    }

    @Test
    void anAdminUpholdsOrDismissesAReport() {
        String id = report("carol", workOf("carol", "Admin report - triaged " + UUID.randomUUID()), "x");

        setStatus("alice", id, "UPHELD", 204);
        assertNull(entry(id, "OPEN"));
        assertEquals("UPHELD", entry(id, "UPHELD").get("status"));

        setStatus("alice", id, "DISMISSED", 204);
        assertEquals("DISMISSED", entry(id, null).get("status"));
    }

    @Test
    void onlyAnAdminTriagesReports() {
        String id = report("bob", workOf("bob", "Admin report - 403 " + UUID.randomUUID()), "x");

        given().auth().oauth2(token("bob")).when().get("/api/admin/reports").then().statusCode(403);
        setStatus("bob", id, "DISMISSED", 403);
        setStatus("alice", id, "NOPE", 400);
        setStatus("alice", UUID.randomUUID().toString(), "UPHELD", 404);
        given().auth().oauth2(token("alice")).queryParam("status", "NOPE")
                .when().get("/api/admin/reports").then().statusCode(400);
    }
}
