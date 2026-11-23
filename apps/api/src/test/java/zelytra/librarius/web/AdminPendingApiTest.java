package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static zelytra.librarius.web.SocialTestSupport.token;

/** #635: what waits for the admins — unread tickets, edits to review, open catalog reports. */
@QuarkusTest
class AdminPendingApiTest {

    private static JsonPath pending() {
        return given().auth().oauth2(token("alice")).when().get("/api/admin/pending")
                .then().statusCode(200).extract().jsonPath();
    }

    @Test
    void countsWhatWaitsAndTheirSum() {
        JsonPath before = pending();

        String item = given().auth().oauth2(token("bob")).contentType("application/json")
                .body(Map.of("book", Map.of("kind", "BOOK", "title", "Pending - " + UUID.randomUUID(), "authors", "Pending"),
                        "status", "TO_READ"))
                .when().post("/api/library").then().statusCode(201).extract().path("id");
        JsonPath book = given().auth().oauth2(token("bob")).when().get("/api/library/" + item).then().statusCode(200)
                .extract().jsonPath();
        given().auth().oauth2(token("bob")).contentType("application/json")
                .body(Map.of("targetType", "EDITION", "targetId", book.getString("book.editionId"),
                        "fields", Map.of("publisher", "Waiting Press")))
                .when().post("/api/catalog-edits").then().statusCode(201);
        given().auth().oauth2(token("bob")).contentType("application/json")
                .body(Map.of("targetType", "WORK", "targetId", book.getString("book.workId"), "reason", "WRONG_INFO"))
                .when().post("/api/reports").then().statusCode(201);
        given().auth().oauth2(token("bob")).contentType("application/json")
                .body(Map.of("kind", "BUG", "message", "Pending ticket " + UUID.randomUUID()))
                .when().post("/api/feedback").then().statusCode(201);

        JsonPath after = pending();
        assertEquals(before.getInt("pendingEdits") + 1, after.getInt("pendingEdits"));
        assertEquals(before.getInt("openReports") + 1, after.getInt("openReports"));
        assertEquals(before.getInt("unreadTickets") + 1, after.getInt("unreadTickets"));
        assertEquals(after.getInt("unreadTickets") + after.getInt("pendingEdits") + after.getInt("openReports"),
                after.getInt("total"));
    }

    @Test
    void onlyAnAdminReadsIt() {
        given().auth().oauth2(token("bob")).when().get("/api/admin/pending").then().statusCode(403);
    }
}
