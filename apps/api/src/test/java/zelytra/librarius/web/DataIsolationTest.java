package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
                "/api/goals", "/api/stats", "/api/series", "/api/genres",
                "/api/catalog/search?q=test" }) {
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
                .body("items.id", hasItem(aliceItem));

        given().auth().oauth2(token("bob"))
                .when().get("/api/library")
                .then().statusCode(200)
                .body("items.id", not(hasItem(aliceItem)));
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
                .body("items.id", hasItem(aliceItem));
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
                .body("items.id", hasItem(aliceWish));

        given().auth().oauth2(token("bob"))
                .when().get("/api/wishlist")
                .then().statusCode(200)
                .body("items.id", not(hasItem(aliceWish)));
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
                .body("items.id", hasItem(aliceWish));
    }

    @Test
    void wishlistItemOfAnotherUserCannotBeEdited() {
        String aliceWish = addWishlistItem("alice", "Isolation - edited wish");

        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("{ \"priority\": \"PRIORITY\", \"estimatedPrice\": 99.00 }")
                .when().put("/api/wishlist/" + aliceWish)
                .then().statusCode(404);

        // Alice's wish kept the priority she gave it.
        given().auth().oauth2(token("alice"))
                .when().get("/api/wishlist")
                .then().statusCode(200)
                .body("items.find { it.id == '" + aliceWish + "' }.priority", is("SOON"));
    }

    /**
     * Acquiring writes on two tables at once — it removes a wish and adds an owned title.
     * Bob must be able to do neither with Alice's wish: no title in his collection, and
     * hers still in her wishlist.
     */
    @Test
    void wishlistItemOfAnotherUserCannotBeAcquired() {
        String aliceWish = addWishlistItem("alice", "Isolation - acquired wish");

        given().auth().oauth2(token("bob")).contentType("application/json").body("{}")
                .when().post("/api/wishlist/" + aliceWish + "/acquire")
                .then().statusCode(404);

        given().auth().oauth2(token("bob"))
                .when().get("/api/library")
                .then().statusCode(200)
                .body("items.book.title", not(hasItem("Isolation - acquired wish")));

        given().auth().oauth2(token("alice"))
                .when().get("/api/wishlist")
                .then().statusCode(200)
                .body("items.id", hasItem(aliceWish));
    }

    /** The budget aggregates the caller's own wishes and nobody else's. */
    @Test
    void theWishlistBudgetOnlyCountsOwnWishes() {
        String marker = "Isolation - budget";
        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "Bob budget", "authors": "%s" },
                          "priority": "SOON", "estimatedPrice": 10.00 }
                        """.formatted(marker))
                .when().post("/api/wishlist").then().statusCode(201);
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "Alice budget", "authors": "%s" },
                          "priority": "SOON", "estimatedPrice": 500.00 }
                        """.formatted(marker))
                .when().post("/api/wishlist").then().statusCode(201);

        double bobBudget = given().auth().oauth2(token("bob")).queryParam("q", marker)
                .when().get("/api/wishlist")
                .then().statusCode(200)
                .body("total", is(1))
                .extract().jsonPath().getDouble("budget.total");
        assertEquals(10.00, bobBudget, 0.001, "Alice's 500 must not show up in Bob's budget");
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

    // ── Series ────────────────────────────────────────────────────────────────

    /** Adds one volume of a series to the user's collection. */
    private void addSeriesVolume(String user, String seriesTitle, int volume) {
        given().auth().oauth2(token(user)).contentType("application/json")
                .body("""
                        { "book": { "kind": "MANGA", "title": "%s vol. %d",
                                    "authors": "Isolation Test", "seriesTitle": "%s",
                                    "volumeNumber": %d },
                          "status": "OWNED" }
                        """.formatted(seriesTitle, volume, seriesTitle, volume))
                .when().post("/api/library")
                .then().statusCode(201);
    }

    private String seriesId(String user, String seriesTitle) {
        return given().auth().oauth2(token(user))
                .when().get("/api/series")
                .then().statusCode(200)
                .extract().path("find { it.title == '" + seriesTitle + "' }.id");
    }

    /**
     * A series is shared catalog data, but the resource is not a catalog browser: it only
     * exposes what the caller owns or follows. A series only Alice collects therefore
     * answers 404 to Bob, on every one of its endpoints.
     */
    @Test
    void seriesOfAnotherUserAreNeitherListedNorReachable() {
        String title = "Isolation - series alice";
        addSeriesVolume("alice", title, 1);
        String aliceSeries = seriesId("alice", title);

        given().auth().oauth2(token("bob"))
                .when().get("/api/series")
                .then().statusCode(200)
                .body("id", not(hasItem(aliceSeries)));

        given().auth().oauth2(token("bob")).when().get("/api/series/" + aliceSeries)
                .then().statusCode(404);
        given().auth().oauth2(token("bob")).when().get("/api/series/" + aliceSeries + "/missing")
                .then().statusCode(404);
        given().auth().oauth2(token("bob")).when().put("/api/series/" + aliceSeries + "/follow")
                .then().statusCode(404);
        given().auth().oauth2(token("bob")).when().delete("/api/series/" + aliceSeries + "/follow")
                .then().statusCode(404);
    }

    /**
     * Two users collecting the same run share the {@code series} row — the catalog is
     * shared. What must not be shared is the follow, nor the ownership counters.
     */
    @Test
    void followStateAndCountersStayPrivateOnASharedSeries() {
        String title = "Isolation - shared series";
        addSeriesVolume("alice", title, 1);
        addSeriesVolume("alice", title, 2);
        addSeriesVolume("bob", title, 1);

        String shared = seriesId("alice", title);
        // Same catalog row for both, so the isolation below is about the user data only.
        given().auth().oauth2(token("bob"))
                .when().get("/api/series")
                .then().statusCode(200)
                .body("id", hasItem(shared));

        given().auth().oauth2(token("alice")).when().put("/api/series/" + shared + "/follow")
                .then().statusCode(204);

        given().auth().oauth2(token("alice")).when().get("/api/series/" + shared)
                .then().statusCode(200)
                .body("followed", is(true))
                .body("ownedCount", is(2));

        given().auth().oauth2(token("bob")).when().get("/api/series/" + shared)
                .then().statusCode(200)
                .body("followed", is(false))
                .body("ownedCount", is(1));
    }

    // ── Genres ────────────────────────────────────────────────────────────────

    /**
     * A genre row is shared catalog data, but the list is not a catalog browser: it holds
     * the genres of the caller's own titles. A genre only Alice collects must therefore be
     * absent from Bob's list, and filtering on it must not reach her collection.
     */
    @Test
    void genresOfAnotherUserAreNeitherListedNorReachable() {
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "Isolation - genre",
                                    "authors": "Isolation Test",
                                    "genres": "Isolation Genre Alice" },
                          "status": "OWNED" }
                        """)
                .when().post("/api/library").then().statusCode(201);

        given().auth().oauth2(token("alice"))
                .when().get("/api/genres")
                .then().statusCode(200)
                .body("code", hasItem("isolation-genre-alice"));

        given().auth().oauth2(token("bob"))
                .when().get("/api/genres")
                .then().statusCode(200)
                .body("code", not(hasItem("isolation-genre-alice")));

        given().auth().oauth2(token("bob")).queryParam("genre", "isolation-genre-alice")
                .when().get("/api/library")
                .then().statusCode(200)
                .body("total", is(0));
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
