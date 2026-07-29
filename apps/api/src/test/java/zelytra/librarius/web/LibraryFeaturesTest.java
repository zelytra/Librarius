package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

/** Categories, rank assignment, reading progress and statistics. */
@QuarkusTest
class LibraryFeaturesTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String token() {
        return keycloak.getAccessToken("bob");
    }

    /**
     * The four shelves every account gets, whichever one asks: the three metals of V1 and
     * the {@code abandon} of V11, all shared rows carrying no {@code user_id}. Asserted for
     * two users rather than one — a built-in that only one account could see would be a
     * seeded row, not a built-in.
     */
    @Test
    void categoriesIncludeTheFourBuiltinsForEveryUser() {
        for (String user : new String[] {"alice", "bob"}) {
            given().auth().oauth2(keycloak.getAccessToken(user))
                    .when().get("/api/categories")
                    .then().statusCode(200)
                    .body("findAll { it.builtin }.code",
                            hasItems("or", "argent", "bronze", "abandon"));
        }
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
