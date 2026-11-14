package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/** Saved library views, their manual order (#451), and a series filed under one category. */
@QuarkusTest
class LibraryViewApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    private String add(String user, String title) {
        return given().auth().oauth2(token(user)).contentType("application/json")
                .body("{ \"book\": { \"kind\": \"BOOK\", \"title\": \"" + title + "\" } }")
                .when().post("/api/library").then().statusCode(201).extract().path("id");
    }

    private String view(String user, String body) {
        return given().auth().oauth2(token(user)).contentType("application/json").body(body)
                .when().post("/api/views").then().statusCode(201).extract().path("id");
    }

    @Test
    void aViewKeepsItsSettingsAndItsManualOrderDrivesTheLibrary() {
        String tag = "View " + UUID.randomUUID();
        String a = add("bob", tag + " A");
        String b = add("bob", tag + " B");
        String c = add("bob", tag + " C");
        String id = view("bob", """
                { "name": "Ma vue", "kind": "BOOK", "shelf": "rank:or", "sort": "manual", "layout": "flat",
                  "groupByCategory": true }""");

        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("{ \"keys\": [\"item:" + c + "\", \"item:" + a + "\", \"item:" + c + "\", \"item:"
                        + UUID.randomUUID() + "\", \"junk\"] }")
                .when().put("/api/views/" + id + "/order")
                .then().statusCode(200)
                // A duplicate, an unknown title and a malformed key are dropped, not refused.
                .body("order", contains("item:" + c, "item:" + a));

        given().auth().oauth2(token("bob")).when().get("/api/views")
                .then().statusCode(200)
                .body("find { it.id == '" + id + "' }.name", is("Ma vue"))
                .body("find { it.id == '" + id + "' }.shelf", is("rank:or"))
                .body("find { it.id == '" + id + "' }.layout", is("flat"))
                .body("find { it.id == '" + id + "' }.groupByCategory", is(true));

        // Placed titles first, in the reader's order; the unplaced one after them.
        given().auth().oauth2(token("bob"))
                .when().get("/api/library?sort=manual&view=" + id + "&q=" + tag)
                .then().statusCode(200)
                .body("items.id", contains(c, a, b));
    }

    @Test
    void aViewIsItsOwnersOnly() {
        String id = view("alice", "{ \"name\": \"Privée\" }");
        given().auth().oauth2(token("bob")).when().get("/api/views")
                .then().statusCode(200).body("id", not(hasItem(id)));
        given().auth().oauth2(token("bob")).contentType("application/json").body("{ \"name\": \"x\" }")
                .when().put("/api/views/" + id).then().statusCode(404);
        given().auth().oauth2(token("bob")).contentType("application/json").body("{ \"keys\": [] }")
                .when().put("/api/views/" + id + "/order").then().statusCode(404);
        given().auth().oauth2(token("bob")).when().delete("/api/views/" + id).then().statusCode(404);
        // Nor can its order be borrowed to sort someone else's library.
        given().auth().oauth2(token("bob")).when().get("/api/library?sort=manual&view=" + id)
                .then().statusCode(400);
        given().auth().oauth2(token("alice")).when().delete("/api/views/" + id).then().statusCode(204);
    }

    @Test
    void anInvalidViewIsRefused() {
        given().auth().oauth2(token("alice")).contentType("application/json").body("{ \"name\": \"\" }")
                .when().post("/api/views").then().statusCode(400);
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"name\": \"x\", \"layout\": \"mosaic\" }")
                .when().post("/api/views").then().statusCode(400);
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"name\": \"x\", \"sort\": \"random\" }")
                .when().post("/api/views").then().statusCode(400);
    }

    @Test
    void aSeriesFiledUnderACategoryMovesAllItsVolumesAndSaysSo() {
        String series = "View series " + UUID.randomUUID();
        for (int volume = 1; volume <= 2; volume++) {
            given().auth().oauth2(token("bob")).contentType("application/json")
                    .body("{ \"book\": { \"kind\": \"MANGA\", \"title\": \"" + series + " Tome " + volume
                            + "\", \"seriesTitle\": \"" + series + "\", \"volumeNumber\": " + volume + " } }")
                    .when().post("/api/library").then().statusCode(201);
        }
        String seriesId = given().auth().oauth2(token("bob")).when().get("/api/series")
                .then().extract().path("find { it.title == '" + series + "' }.id");
        String gold = given().auth().oauth2(token("bob")).when().get("/api/categories")
                .then().extract().path("find { it.code == 'or' }.id");

        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("{ \"categoryId\": \"" + gold + "\" }")
                .when().put("/api/series/" + seriesId + "/rank").then().statusCode(204);

        given().auth().oauth2(token("bob")).when().get("/api/series")
                .then().body("find { it.id == '" + seriesId + "' }.rankCode", is("or"));
        List<String> ranks = given().auth().oauth2(token("bob")).when().get("/api/library?rank=or&q=" + series)
                .then().statusCode(200).extract().path("items.rankCode");
        org.junit.jupiter.api.Assertions.assertEquals(List.of("or", "or"), ranks);

        // Nothing of it held: nothing to file.
        given().auth().oauth2(token("alice")).contentType("application/json").body("{}")
                .when().put("/api/series/" + seriesId + "/rank").then().statusCode(404);
    }
}
