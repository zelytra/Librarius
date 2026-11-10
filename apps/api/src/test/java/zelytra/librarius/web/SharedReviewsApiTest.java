package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** #205-#208: a review shared by one reader, as another one sees it. */
@QuarkusTest
class SharedReviewsApiTest {

    private static final KeycloakTestClient KEYCLOAK = new KeycloakTestClient();

    private static String token(String user) {
        return KEYCLOAK.getAccessToken(user);
    }

    private static String id(String user) {
        return given().auth().oauth2(token(user)).when().get("/api/me").then().statusCode(200).extract().path("id");
    }

    private static void relation(String from, String to, String what, boolean on) {
        var req = given().auth().oauth2(token(from)).when();
        (on ? req.put("/api/users/" + id(to) + "/" + what) : req.delete("/api/users/" + id(to) + "/" + what))
                .then().statusCode(204);
    }

    /** Adds the same title for a user, and answers its library item and work. */
    private static String[] add(String user, String title) {
        JsonPath item = given().auth().oauth2(token(user)).contentType("application/json")
                .body("{ \"book\": { \"kind\": \"BOOK\", \"title\": \"" + title + "\", \"authors\": \"Shared Author\" }, \"status\": \"READ\" }")
                .when().post("/api/library").then().statusCode(201).extract().jsonPath();
        return new String[] {item.getString("id"), item.getString("book.workId")};
    }

    private static void review(String user, String itemId, int rating, String text, Boolean shared) {
        given().auth().oauth2(token(user)).contentType("application/json")
                .body("{ \"rating\": " + rating + ", \"review\": \"" + text + "\""
                        + (shared == null ? "" : ", \"shared\": " + shared) + " }")
                .when().put("/api/library/" + itemId + "/review").then().statusCode(200);
    }

    private static JsonPath reviews(String user, String workId) {
        return given().auth().oauth2(token(user)).when().get("/api/works/" + workId + "/reviews")
                .then().statusCode(200).extract().jsonPath();
    }

    @BeforeEach
    void noRelationship() {
        relation("alice", "bob", "block", false);
        relation("bob", "alice", "block", false);
        relation("alice", "bob", "follow", false);
        relation("bob", "alice", "follow", false);
    }

    @Test
    void aSharedReviewReachesWhoCanSeeItsAuthorAndAPrivateOneNobody() {
        String title = "Shared " + UUID.randomUUID();
        String[] bobs = add("bob", title);
        String[] alices = add("alice", title);
        assertEquals(bobs[1], alices[1], "the same work for both");

        review("bob", bobs[0], 4, "Très bien", true);
        review("alice", alices[0], 2, "Bof", null); // no flag: stays private

        // Strangers: bob's account is not public, so alice sees nothing yet.
        assertEquals(0, reviews("alice", bobs[1]).getInt("count"));

        relation("alice", "bob", "follow", true);
        relation("bob", "alice", "follow", true);
        JsonPath seen = reviews("alice", bobs[1]);
        assertEquals(1, seen.getInt("count"));
        assertEquals("Très bien", seen.getString("reviews[0].review"));
        // Alice's own private 2/5 moves nothing.
        assertEquals(4.0, seen.getDouble("average"));

        // A block hides it again, whoever blocked.
        relation("bob", "alice", "block", true);
        assertEquals(0, reviews("alice", bobs[1]).getInt("count"));
        assertNull(reviews("alice", bobs[1]).get("average"));
    }

    @Test
    void onlyASharedReviewCanBeReported() {
        String title = "Reported " + UUID.randomUUID();
        String[] bobs = add("bob", title);
        review("bob", bobs[0], 1, "Spoiler", false);

        String report = "{ \"targetType\": \"REVIEW\", \"targetId\": \"" + bobs[0] + "\", \"reason\": \"SPOILER\" }";
        given().auth().oauth2(token("alice")).contentType("application/json").body(report)
                .when().post("/api/reports").then().statusCode(400);

        review("bob", bobs[0], 1, "Spoiler", true);
        given().auth().oauth2(token("alice")).contentType("application/json").body(report)
                .when().post("/api/reports").then().statusCode(201);
    }
}
