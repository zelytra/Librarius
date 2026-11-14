package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/** The people a reader follows who have a title too (#452), within the visibility rule. */
@QuarkusTest
class FriendsOnWorkApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    private String id(String user) {
        return given().auth().oauth2(token(user)).when().get("/api/me")
                .then().statusCode(200).extract().path("id");
    }

    private void follow(String actor, String target, boolean on) {
        var request = given().auth().oauth2(token(actor)).when();
        (on ? request.put("/api/users/" + target + "/follow") : request.delete("/api/users/" + target + "/follow"))
                .then().statusCode(204);
    }

    private void makePrivate(String user) {
        given().auth().oauth2(token(user)).contentType("application/json")
                .body("{ \"displayName\": \"" + user + "\", \"locale\": \"fr\", \"timeZone\": \"Europe/Paris\", \"publicAccount\": false }")
                .when().patch("/api/me").then().statusCode(200);
    }

    @Test
    void listsWhoTheReaderFollowsWithTheirStatusAndCategoryAndOnlyWhenVisible() {
        String alice = id("alice");
        String bob = id("bob");
        makePrivate("alice");
        String title = "Friends on work " + UUID.randomUUID();
        String body = "{ \"book\": { \"kind\": \"BOOK\", \"title\": \"" + title + "\", \"authors\": \"Friend Author\" }, "
                + "\"status\": \"READ\" }";
        String bobsItem = given().auth().oauth2(token("bob")).contentType("application/json").body(body)
                .when().post("/api/library").then().statusCode(201).extract().path("id");
        String workId = given().auth().oauth2(token("bob")).when().get("/api/library/" + bobsItem)
                .then().extract().path("book.workId");
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body(body.replace("\"READ\"", "\"READING\""))
                .when().post("/api/library").then().statusCode(201);
        String aliceItem = given().auth().oauth2(token("alice")).when().get("/api/library?q=" + title)
                .then().extract().path("items[0].id");
        String gold = given().auth().oauth2(token("alice")).when().get("/api/categories")
                .then().statusCode(200).extract().path("find { it.code == 'or' }.id");
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"categoryId\": \"" + gold + "\" }")
                .when().put("/api/library/" + aliceItem + "/rank").then().statusCode(200);

        try {
            // A one-way follow of a private account reveals nothing.
            follow("bob", alice, true);
            given().auth().oauth2(token("bob")).when().get("/api/works/" + workId + "/friends")
                    .then().statusCode(200).body("$", empty());

            // Followed back: Alice shows, reading it, filed under her category, rating kept private.
            follow("alice", bob, true);
            given().auth().oauth2(token("bob")).when().get("/api/works/" + workId + "/friends")
                    .then().statusCode(200)
                    .body("$", hasSize(1))
                    .body("[0].id", is(alice))
                    .body("[0].status", is("READING"))
                    .body("[0].ownership", is("OWNED"))
                    .body("[0].wished", is(false))
                    .body("[0].rankCode", is("or"))
                    .body("[0].rating", nullValue());
        } finally {
            follow("bob", alice, false);
            follow("alice", bob, false);
        }
    }
}
