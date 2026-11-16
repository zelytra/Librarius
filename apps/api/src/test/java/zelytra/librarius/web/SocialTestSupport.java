package zelytra.librarius.web;

import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.path.json.JsonPath;

import java.util.List;

import static io.restassured.RestAssured.given;

/** The alice/bob/carol moves the social tests share (#497, #209, #210). */
final class SocialTestSupport {

    private static final KeycloakTestClient KEYCLOAK = new KeycloakTestClient();
    static final List<String> MEMBERS = List.of("alice", "bob", "carol");

    private SocialTestSupport() {
    }

    static String token(String user) {
        return KEYCLOAK.getAccessToken(user);
    }

    static String id(String user) {
        return given().auth().oauth2(token(user)).when().get("/api/me").then().statusCode(200).extract().path("id");
    }

    static void relation(String from, String to, String what, boolean on) {
        var req = given().auth().oauth2(token(from)).when();
        (on ? req.put("/api/users/" + id(to) + "/" + what) : req.delete("/api/users/" + id(to) + "/" + what))
                .then().statusCode(204);
    }

    static void mutual(String a, String b) {
        relation(a, b, "follow", true);
        relation(b, a, "follow", true);
    }

    /** Sets the preference, sending the rest of the profile back as it stands (a full replacement). */
    static void publicAccount(String user, boolean on) {
        JsonPath me = given().auth().oauth2(token(user)).when().get("/api/me").then().statusCode(200)
                .extract().jsonPath();
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("displayName", me.getString("displayName") == null ? user : me.getString("displayName"));
        body.put("locale", me.getString("locale") == null ? "fr" : me.getString("locale"));
        body.put("timeZone", me.getString("timeZone"));
        body.put("publicAccount", on);
        given().auth().oauth2(token(user)).contentType("application/json").body(body)
                .when().patch("/api/me").then().statusCode(200);
    }

    /** No follow, no block, no public account between the three members. */
    static void strangers() {
        for (String a : MEMBERS) {
            publicAccount(a, false);
            for (String b : MEMBERS) {
                if (!a.equals(b)) {
                    relation(a, b, "block", false);
                    relation(a, b, "follow", false);
                }
            }
        }
    }

    /** Adds a title to a member's library with a status; answers the item. */
    static JsonPath add(String user, String title, String status) {
        return given().auth().oauth2(token(user)).contentType("application/json")
                .body("{ \"book\": { \"kind\": \"BOOK\", \"title\": \"" + title
                        + "\", \"authors\": \"Social Author\", \"genres\": \"Fantasy\" }, \"status\": \"" + status + "\" }")
                .when().post("/api/library").then().statusCode(201).extract().jsonPath();
    }

    static void shareReview(String user, String itemId, double rating, String text) {
        given().auth().oauth2(token(user)).contentType("application/json")
                .body("{ \"rating\": " + rating + ", \"review\": \"" + text + "\", \"shared\": true }")
                .when().put("/api/library/" + itemId + "/review").then().statusCode(200);
    }
}
