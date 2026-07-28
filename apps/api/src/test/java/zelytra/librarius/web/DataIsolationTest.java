package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Locks down data isolation between users.
 *
 * <p>There is no PostgreSQL RLS: the only barrier is the application-level filtering on
 * {@code user_id} in the repositories. A query forgetting that filter would expose another
 * user's library without any other test noticing. Every scoped resource is therefore
 * exercised here with two distinct accounts.
 *
 * <p>Assertions are deliberately relative ("Alice's identifier does not show up for Bob")
 * rather than absolute: the database is shared by the whole test suite.
 */
@QuarkusTest
class DataIsolationTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    /** Adds a title to the user's collection and returns its identifier. */
    private String addLibraryItem(String user, String title, String status) {
        return given().auth().oauth2(token(user)).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "%s", "authors": "Isolation Test" },
                          "status": "%s" }
                        """.formatted(title, status))
                .when().post("/api/library")
                .then().statusCode(201)
                .extract().path("id");
    }

    /** Adds a wish to the user's wishlist and returns its identifier. */
    private String addWishlistItem(String user, String title) {
        return given().auth().oauth2(token(user)).contentType("application/json")
                .body("""
                        { "book": { "kind": "MANGA", "title": "%s", "authors": "Isolation Test" },
                          "priority": "SOON" }
                        """.formatted(title))
                .when().post("/api/wishlist")
                .then().statusCode(201)
                .extract().path("id");
    }

    // ── Missing token ─────────────────────────────────────────────────────────

    @Test
    void everyScopedResourceRejectsAnonymousAccess() {
        for (String path : new String[] {
                "/api/me", "/api/library", "/api/wishlist", "/api/categories",
                "/api/goals", "/api/stats", "/api/catalog/search?q=test" }) {
            given().when().get(path)
                    .then().statusCode(401);
        }
    }

    // ── Library ───────────────────────────────────────────────────────────────

    @Test
    void libraryItemsAreInvisibleToOtherUsers() {
        String aliceItem = addLibraryItem("alice", "Isolation - library", "OWNED");

        given().auth().oauth2(token("alice"))
                .when().get("/api/library")
                .then().statusCode(200)
                .body("id", hasItem(aliceItem));

        given().auth().oauth2(token("bob"))
                .when().get("/api/library")
                .then().statusCode(200)
                .body("id", not(hasItem(aliceItem)));
    }

    /**
     * Accessing someone else's identifier must answer 404 and not 403: a 403 would confirm
     * that the resource exists, which is already an information leak.
     */
    @Test
    void libraryItemOfAnotherUserCannotBeDeleted() {
        String aliceItem = addLibraryItem("alice", "Isolation - deletion", "OWNED");

        given().auth().oauth2(token("bob"))
                .when().delete("/api/library/" + aliceItem)
                .then().statusCode(404);

        // Alice's item is still there.
        given().auth().oauth2(token("alice"))
                .when().get("/api/library")
                .then().statusCode(200)
                .body("id", hasItem(aliceItem));
    }

    @Test
    void libraryItemOfAnotherUserCannotBeRanked() {
        String aliceItem = addLibraryItem("alice", "Isolation - rank", "OWNED");
        String orId = given().auth().oauth2(token("bob")).when().get("/api/categories")
                .then().statusCode(200)
                .extract().path("find { it.code == 'or' }.id");

        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("{ \"categoryId\": \"" + orId + "\" }")
                .when().put("/api/library/" + aliceItem + "/rank")
                .then().statusCode(404);
    }

    @Test
    void libraryItemOfAnotherUserProgressCannotBeUpdated() {
        String aliceItem = addLibraryItem("alice", "Isolation - progress", "READING");

        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("{ \"currentPage\": 999, \"percent\": 99, \"status\": \"READ\" }")
                .when().put("/api/library/" + aliceItem + "/progress")
                .then().statusCode(404);
    }

    // ── Wishlist ──────────────────────────────────────────────────────────────

    @Test
    void wishlistIsInvisibleToOtherUsers() {
        String aliceWish = addWishlistItem("alice", "Isolation - wish");

        given().auth().oauth2(token("alice"))
                .when().get("/api/wishlist")
                .then().statusCode(200)
                .body("id", hasItem(aliceWish));

        given().auth().oauth2(token("bob"))
                .when().get("/api/wishlist")
                .then().statusCode(200)
                .body("id", not(hasItem(aliceWish)));
    }

    @Test
    void wishlistItemOfAnotherUserCannotBeDeleted() {
        String aliceWish = addWishlistItem("alice", "Isolation - protected wish");

        given().auth().oauth2(token("bob"))
                .when().delete("/api/wishlist/" + aliceWish)
                .then().statusCode(404);

        given().auth().oauth2(token("alice"))
                .when().get("/api/wishlist")
                .then().statusCode(200)
                .body("id", hasItem(aliceWish));
    }

    // ── Categories ────────────────────────────────────────────────────────────

    /**
     * Built-ins (Or / Argent / Bronze) carry {@code user_id NULL} and are therefore
     * visible to everyone; a category created by a user is visible only to that user.
     */
    @Test
    void customCategoriesAreNotSharedButBuiltinsAre() {
        String aliceCategory = given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"label\": \"Isolation Alice\", \"color\": \"#123456\" }")
                .when().post("/api/categories")
                .then().statusCode(200)
                .body("builtin", is(false))
                .extract().path("id");

        given().auth().oauth2(token("alice"))
                .when().get("/api/categories")
                .then().statusCode(200)
                .body("id", hasItem(aliceCategory));

        given().auth().oauth2(token("bob"))
                .when().get("/api/categories")
                .then().statusCode(200)
                .body("id", not(hasItem(aliceCategory)))
                // Built-ins stay shared.
                .body("code", hasItem("or"));
    }

    /** Bob cannot file his own title under a category owned by Alice. */
    @Test
    void categoryOfAnotherUserCannotBeAssigned() {
        String aliceCategory = given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"label\": \"Isolation Alice Rang\", \"color\": \"#654321\" }")
                .when().post("/api/categories")
                .then().statusCode(200)
                .extract().path("id");

        String bobItem = addLibraryItem("bob", "Isolation - cross-user rank", "OWNED");

        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("{ \"categoryId\": \"" + aliceCategory + "\" }")
                .when().put("/api/library/" + bobItem + "/rank")
                .then().statusCode(400);
    }

    // ── Goals ─────────────────────────────────────────────────────────────────

    /** Deliberately distant years so as not to clash with the other tests. */
    @Test
    void goalsAreIsolatedForTheSameYear() {
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"targetCount\": 11, \"unit\": \"BOOKS\" }")
                .when().put("/api/goals/2991")
                .then().statusCode(200).body("targetCount", is(11));

        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("{ \"targetCount\": 22, \"unit\": \"BOOKS\" }")
                .when().put("/api/goals/2991")
                .then().statusCode(200).body("targetCount", is(22));

        // Each user keeps their own target for the same year.
        given().auth().oauth2(token("alice"))
                .when().get("/api/goals")
                .then().statusCode(200)
                .body("find { it.year == 2991 }.targetCount", is(11));

        given().auth().oauth2(token("bob"))
                .when().get("/api/goals")
                .then().statusCode(200)
                .body("find { it.year == 2991 }.targetCount", is(22));
    }

    // ── Statistics ────────────────────────────────────────────────────────────

    /**
     * Statistics aggregate the user's own collection: a read title added by Alice must
     * not change Bob's counters in any way.
     */
    @Test
    void statsOnlyCountOwnItems() {
        int bobReadBefore = given().auth().oauth2(token("bob"))
                .when().get("/api/stats")
                .then().statusCode(200)
                .extract().jsonPath().getInt("read");

        addLibraryItem("alice", "Isolation - statistics", "READ");

        given().auth().oauth2(token("bob"))
                .when().get("/api/stats")
                .then().statusCode(200)
                .body("read", is(bobReadBefore));
    }
}
