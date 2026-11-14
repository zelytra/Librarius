package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

/** One action applied to several titles at once (#457). */
@QuarkusTest
class BulkUpdateApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    private String add(String user, String title) {
        return given().auth().oauth2(token(user)).contentType("application/json")
                .body("{ \"book\": { \"kind\": \"BOOK\", \"title\": \"" + title + "\" } }")
                .when().post("/api/library").then().statusCode(201).extract().path("id");
    }

    @Test
    void filesRatesAndSetsSeveralTitlesAtOnceAndOnlyTheCallers() {
        String tag = "Bulk " + UUID.randomUUID();
        String a = add("bob", tag + " A");
        String b = add("bob", tag + " B");
        String alices = add("alice", tag + " C");
        String gold = given().auth().oauth2(token("bob")).when().get("/api/categories")
                .then().extract().path("find { it.code == 'or' }.id");

        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("{ \"ids\": [\"" + a + "\", \"" + b + "\", \"" + alices + "\"], \"status\": \"READ\", "
                        + "\"ownership\": \"EBOOK\", \"format\": \"POCKET\", \"categoryId\": \"" + gold + "\", \"rating\": 4 }")
                .when().post("/api/library/bulk")
                .then().statusCode(200).body("updated", is(2));

        given().auth().oauth2(token("bob")).when().get("/api/library?q=" + tag)
                .then().statusCode(200)
                .body("items", hasSize(2))
                .body("items.status", everyItem(is("READ")))
                .body("items.ownership", everyItem(is("EBOOK")))
                .body("items.format", everyItem(is("POCKET")))
                .body("items.rankCode", everyItem(is("or")))
                .body("items.rating", everyItem(is(4)));
        // Another reader's title, named in the same request, is untouched.
        given().auth().oauth2(token("alice")).when().get("/api/library/" + alices)
                .then().statusCode(200).body("status", is("TO_READ")).body("rating", org.hamcrest.Matchers.nullValue());

        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("{ \"ids\": [\"" + a + "\"], \"clearCategory\": true, \"clearRating\": true }")
                .when().post("/api/library/bulk").then().statusCode(200);
        given().auth().oauth2(token("bob")).when().get("/api/library/" + a)
                .then().body("rankCode", org.hamcrest.Matchers.nullValue()).body("rating", org.hamcrest.Matchers.nullValue());

        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("{ \"ids\": [\"" + a + "\", \"" + b + "\"], \"remove\": true }")
                .when().post("/api/library/bulk").then().statusCode(200).body("updated", is(2));
        given().auth().oauth2(token("bob")).when().get("/api/library?q=" + tag)
                .then().body("items", hasSize(0));
    }

    @Test
    void refusesAnotherReadersCategoryAndAnEmptySelection() {
        String id = add("bob", "Bulk refuse " + UUID.randomUUID());
        // Alice's own category: the built-ins are shared rows, a custom one is hers alone.
        String alicesGold = given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"label\": \"Bulk " + UUID.randomUUID().toString().substring(0, 8) + "\" }")
                .when().post("/api/categories").then().extract().path("id");
        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("{ \"ids\": [\"" + id + "\"], \"categoryId\": \"" + alicesGold + "\" }")
                .when().post("/api/library/bulk").then().statusCode(400);
        given().auth().oauth2(token("bob")).contentType("application/json").body("{ \"ids\": [] }")
                .when().post("/api/library/bulk").then().statusCode(400);
        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("{ \"ids\": [\"" + id + "\"], \"rating\": 9 }")
                .when().post("/api/library/bulk").then().statusCode(400);
    }
}
