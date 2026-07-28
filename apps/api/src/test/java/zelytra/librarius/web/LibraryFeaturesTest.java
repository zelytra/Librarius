package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

/** Categories, rank assignment, reading progress and statistics. */
@QuarkusTest
class LibraryFeaturesTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String token() {
        return keycloak.getAccessToken("bob");
    }

    @Test
    void categoriesIncludeBuiltins() {
        given().auth().oauth2(token())
                .when().get("/api/categories")
                .then().statusCode(200)
                .body("code", hasItem("or"));
    }

    @Test
    void assignRankAndReadStatsAndProgress() {
        String token = token();

        // Create a title currently being read.
        String itemId = given().auth().oauth2(token).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "Iron Flame", "authors": "Rebecca Yarros" },
                          "status": "READING" }
                        """)
                .when().post("/api/library")
                .then().statusCode(201).extract().path("id");

        // Fetch the id of the built-in "or" (gold) category.
        String orId = given().auth().oauth2(token).when().get("/api/categories")
                .then().statusCode(200)
                .extract().path("find { it.code == 'or' }.id");

        // Assign the gold rank.
        given().auth().oauth2(token).contentType("application/json")
                .body("{ \"categoryId\": \"" + orId + "\" }")
                .when().put("/api/library/" + itemId + "/rank")
                .then().statusCode(200).body("rankCode", is("or"));

        // Update the reading progress.
        given().auth().oauth2(token).contentType("application/json")
                .body("{ \"currentPage\": 120, \"percent\": 35, \"status\": \"READING\" }")
                .when().put("/api/library/" + itemId + "/progress")
                .then().statusCode(204);

        // The stats count at least this in-progress title.
        given().auth().oauth2(token).when().get("/api/stats")
                .then().statusCode(200)
                .body("reading", greaterThanOrEqualTo(1));
    }
}
