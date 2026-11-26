package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * #646: a title carries at most one rank (Platine, Or, Argent, Bronze, Abandon) and any number
 * of the reader's own categories — "Or + Polar" — each set without disturbing the other.
 */
@QuarkusTest
class LibraryCategoriesTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private static String item(String token) {
        return given().auth().oauth2(token).contentType("application/json")
                .body("{ \"book\": { \"kind\": \"BOOK\", \"title\": \"Polar " + UUID.randomUUID()
                        + "\", \"authors\": \"A. Writer\" }, \"status\": \"READ\" }")
                .when().post("/api/library")
                .then().statusCode(201).extract().path("id");
    }

    private static String category(String token, String label) {
        return given().auth().oauth2(token).contentType("application/json")
                .body("{ \"label\": \"" + label + "\" }")
                .when().post("/api/categories")
                .then().statusCode(200).extract().path("id");
    }

    private static String code(String token, String id) {
        return given().auth().oauth2(token).when().get("/api/categories")
                .then().statusCode(200).extract().path("find { it.id == '" + id + "' }.code");
    }

    private static String builtin(String token, String code) {
        return given().auth().oauth2(token).when().get("/api/categories")
                .then().statusCode(200).extract().path("find { it.code == '" + code + "' }.id");
    }

    @Test
    void aTitleCarriesARankAndSeveralOfTheReadersCategories() {
        String token = keycloak.getAccessToken("alice");
        String itemId = item(token);
        String polar = category(token, "Polar " + UUID.randomUUID().toString().substring(0, 6));
        String nordic = category(token, "Nordique " + UUID.randomUUID().toString().substring(0, 6));

        given().auth().oauth2(token).contentType("application/json")
                .body("{ \"categoryId\": \"" + builtin(token, "or") + "\" }")
                .when().put("/api/library/" + itemId + "/rank")
                .then().statusCode(200).body("rankCode", is("or"));
        given().auth().oauth2(token).contentType("application/json")
                .body("{ \"categoryIds\": [\"" + polar + "\", \"" + nordic + "\"] }")
                .when().put("/api/library/" + itemId + "/categories")
                .then().statusCode(200)
                .body("rankCode", is("or"))
                .body("categoryCodes", containsInAnyOrder(code(token, polar), code(token, nordic)));

        // Changing the rank leaves the categories alone, and the other way round.
        given().auth().oauth2(token).contentType("application/json")
                .body("{ \"categoryId\": \"" + builtin(token, "argent") + "\" }")
                .when().put("/api/library/" + itemId + "/rank")
                .then().statusCode(200).body("rankCode", is("argent"))
                .body("categoryCodes", containsInAnyOrder(code(token, polar), code(token, nordic)));
        given().auth().oauth2(token).contentType("application/json")
                .body("{ \"categoryIds\": [\"" + nordic + "\"] }")
                .when().put("/api/library/" + itemId + "/categories")
                .then().statusCode(200).body("rankCode", is("argent"))
                .body("categoryCodes", contains(code(token, nordic)));
        given().auth().oauth2(token).contentType("application/json")
                .body("{ \"categoryIds\": [] }")
                .when().put("/api/library/" + itemId + "/categories")
                .then().statusCode(200).body("categoryCodes", empty());
    }

    @Test
    void theCollectionFilterFindsATitleByAnyOfItsCategories() {
        String token = keycloak.getAccessToken("alice");
        String itemId = item(token);
        String polar = category(token, "Polar " + UUID.randomUUID().toString().substring(0, 6));
        given().auth().oauth2(token).contentType("application/json")
                .body("{ \"categoryId\": \"" + builtin(token, "or") + "\" }")
                .when().put("/api/library/" + itemId + "/rank").then().statusCode(200);
        given().auth().oauth2(token).contentType("application/json")
                .body("{ \"categoryIds\": [\"" + polar + "\"] }")
                .when().put("/api/library/" + itemId + "/categories").then().statusCode(200);

        given().auth().oauth2(token).queryParam("rank", code(token, polar)).queryParam("size", 100)
                .when().get("/api/library")
                .then().statusCode(200).body("items.id", hasItem(itemId));
        given().auth().oauth2(token).queryParam("rank", "or").queryParam("size", 100)
                .when().get("/api/library")
                .then().statusCode(200).body("items.id", hasItem(itemId));
    }

    /**
     * The rank route still files a title under any category, as it did before (#646): a
     * built-in becomes its rank, one of the reader's own is added to its categories — the rank
     * and the other categories untouched. The categories route takes the reader's own only.
     */
    @Test
    void theRankRouteAddsACustomCategoryAndTheCategoriesRouteRefusesABuiltIn() {
        String token = keycloak.getAccessToken("alice");
        String itemId = item(token);
        String polar = category(token, "Polar " + UUID.randomUUID().toString().substring(0, 6));
        given().auth().oauth2(token).contentType("application/json")
                .body("{ \"categoryId\": \"" + builtin(token, "or") + "\" }")
                .when().put("/api/library/" + itemId + "/rank").then().statusCode(200);

        given().auth().oauth2(token).contentType("application/json")
                .body("{ \"categoryId\": \"" + polar + "\" }")
                .when().put("/api/library/" + itemId + "/rank")
                .then().statusCode(200)
                .body("rankCode", is("or"))
                .body("categoryCodes", contains(code(token, polar)));
        // Clearing the rank leaves the reader's categories.
        given().auth().oauth2(token).contentType("application/json")
                .body("{ \"categoryId\": null }")
                .when().put("/api/library/" + itemId + "/rank")
                .then().statusCode(200)
                .body("rankCode", is((String) null))
                .body("categoryCodes", contains(code(token, polar)));
        given().auth().oauth2(token).contentType("application/json")
                .body("{ \"categoryIds\": [\"" + builtin(token, "or") + "\"] }")
                .when().put("/api/library/" + itemId + "/categories")
                .then().statusCode(400);
    }

    @Test
    void anotherReadersTitleOrCategoryIsRefused() {
        String alice = keycloak.getAccessToken("alice");
        String bob = keycloak.getAccessToken("bob");
        String alicesItem = item(alice);
        String bobsItem = item(bob);
        String alicesPolar = category(alice, "Polar " + UUID.randomUUID().toString().substring(0, 6));

        given().auth().oauth2(bob).contentType("application/json")
                .body("{ \"categoryIds\": [] }")
                .when().put("/api/library/" + alicesItem + "/categories")
                .then().statusCode(404);
        given().auth().oauth2(bob).contentType("application/json")
                .body("{ \"categoryIds\": [\"" + alicesPolar + "\"] }")
                .when().put("/api/library/" + bobsItem + "/categories")
                .then().statusCode(400);
        given().auth().oauth2(bob).when().get("/api/library/" + bobsItem)
                .then().statusCode(200).body("categoryCodes", not(hasItem(code(alice, alicesPolar))));
    }

    /** An archive written before #646 holds a custom category in `rankCode`: it restores as a category. */
    @Test
    void anOlderArchivesCustomRankRestoresAsACategory() {
        String token = keycloak.getAccessToken("alice");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String code = "archive-" + suffix;
        String title = "Archived " + suffix;
        given().auth().oauth2(token).contentType("application/json")
                .body("""
                        { "schemaVersion": 1,
                          "categories": [ { "code": "%s", "label": "Archive %s", "sortOrder": 9 } ],
                          "collection": [ { "book": { "kind": "BOOK", "title": "%s", "authors": "A. Writer" },
                                            "status": "READ", "rankCode": "%s" } ] }
                        """.formatted(code, suffix, title, code))
                .when().post("/api/import/json")
                .then().statusCode(200).body("imported", is(1));

        given().auth().oauth2(token).queryParam("q", title)
                .when().get("/api/library")
                .then().statusCode(200)
                .body("items[0].rankCode", is((String) null))
                .body("items[0].categoryCodes", contains(code));
    }
}
