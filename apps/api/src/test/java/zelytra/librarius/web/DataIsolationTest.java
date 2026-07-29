package zelytra.librarius.web;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.DatePrecision;
import zelytra.librarius.domain.ReleaseConfidence;
import zelytra.librarius.domain.ReleaseRegion;
import zelytra.librarius.domain.UpcomingRelease;
import zelytra.librarius.domain.repository.SeriesRepository;
import zelytra.librarius.domain.repository.UpcomingReleaseRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    @Inject
    SeriesRepository series;

    @Inject
    UpcomingReleaseRepository releases;

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
                "/api/goals", "/api/stats", "/api/stats/timeline", "/api/series", "/api/genres",
                "/api/works/00000000-0000-0000-0000-000000000000/editions",
                "/api/export", "/api/releases/upcoming",
                "/api/catalog/search?q=test" }) {
            given().when().get(path)
                    .then().statusCode(401);
        }
        given().when().delete("/api/me").then().statusCode(401);
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

    /**
     * A rating and a review are the most private thing the application stores: they are
     * what the user thought, written for nobody else. Bob can neither write them on Alice's
     * item nor read the ones she wrote — and the refusal is a 404, so he does not even
     * learn that the item exists.
     */
    @Test
    void libraryItemOfAnotherUserCannotBeReviewed() {
        String aliceItem = addLibraryItem("alice", "Isolation - review", "READ");

        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"rating\": 5, \"review\": \"Note privée d'Alice.\" }")
                .when().put("/api/library/" + aliceItem + "/review")
                .then().statusCode(200);

        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("{ \"rating\": 1, \"review\": \"Bob passait par là.\" }")
                .when().put("/api/library/" + aliceItem + "/review")
                .then().statusCode(404);

        given().auth().oauth2(token("bob"))
                .when().get("/api/library/" + aliceItem)
                .then().statusCode(404);

        // Alice's own rating and review came through untouched.
        given().auth().oauth2(token("alice"))
                .when().get("/api/library/" + aliceItem)
                .then().statusCode(200)
                .body("rating", is(5))
                .body("review", is("Note privée d'Alice."));
    }

    /** A review never leaves its owner's collection, not even through the paged listing. */
    @Test
    void reviewsAreNotVisibleInAnotherUsersCollection() {
        String marker = "Isolation - private review";
        String aliceItem = addLibraryItem("alice", marker, "READ");
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"rating\": 5, \"review\": \"Secret.\" }")
                .when().put("/api/library/" + aliceItem + "/review")
                .then().statusCode(200);

        given().auth().oauth2(token("bob")).queryParam("q", marker)
                .when().get("/api/library")
                .then().statusCode(200)
                .body("total", is(0));
    }

    // ── Editions ──────────────────────────────────────────────────────────────

    /** Adds a title with a given publisher, and returns the created item. */
    private String addEdition(String user, String title, String publisher) {
        return given().auth().oauth2(token(user)).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "%s", "authors": "Isolation Test",
                                    "publisher": "%s", "pageCount": 300 },
                          "status": "OWNED" }
                        """.formatted(title, publisher))
                .when().post("/api/library")
                .then().statusCode(201)
                .extract().path("id");
    }

    private String bookField(String user, String itemId, String field) {
        return given().auth().oauth2(token(user))
                .when().get("/api/library/" + itemId)
                .then().statusCode(200)
                .extract().path("book." + field);
    }

    /**
     * A work and its editions are shared catalog data, but the resource listing them is not
     * a catalog browser: it opens on a work the caller owns something of, and answers 404
     * everywhere else — the same answer an unknown identifier gets, so that a work
     * identifier cannot be used to probe someone else's shelves.
     */
    @Test
    void editionsOfAWorkTheCallerOwnsNothingOfAreNotReachable() {
        String aliceItem = addEdition("alice", "Isolation - éditions", "Pocket");
        String workId = bookField("alice", aliceItem, "workId");

        given().auth().oauth2(token("alice"))
                .when().get("/api/works/" + workId + "/editions")
                .then().statusCode(200);

        given().auth().oauth2(token("bob"))
                .when().get("/api/works/" + workId + "/editions")
                .then().statusCode(404);
    }

    /**
     * Two readers of the same title share the work and see the same editions — that is the
     * catalog doing its job. What must not be shared is the ownership: {@code owned} flags
     * the caller's own copy and nobody else's.
     */
    @Test
    void theOwnedFlagOnASharedWorkIsPerCaller() {
        String title = "Isolation - éditions partagées";
        String aliceItem = addEdition("alice", title, "Pocket");
        String bobItem = addEdition("bob", title, "Gallimard");

        String workId = bookField("alice", aliceItem, "workId");
        assertEquals(workId, bookField("bob", bobItem, "workId"),
                "the catalog row is shared by the two readers");

        String aliceEdition = bookField("alice", aliceItem, "editionId");
        String bobEdition = bookField("bob", bobItem, "editionId");

        given().auth().oauth2(token("alice"))
                .when().get("/api/works/" + workId + "/editions")
                .then().statusCode(200)
                .body("find { it.id == '" + aliceEdition + "' }.owned", is(true))
                .body("find { it.id == '" + bobEdition + "' }.owned", is(false));

        given().auth().oauth2(token("bob"))
                .when().get("/api/works/" + workId + "/editions")
                .then().statusCode(200)
                .body("find { it.id == '" + bobEdition + "' }.owned", is(true))
                .body("find { it.id == '" + aliceEdition + "' }.owned", is(false));
    }

    /**
     * The edition is the only thing shared here: the row saying who owns it is not. Bob
     * cannot move Alice's copy onto another edition, even one he owns himself — and the
     * refusal is a 404, so he does not learn that her item exists.
     */
    @Test
    void libraryItemOfAnotherUserCannotBeMovedToAnotherEdition() {
        String title = "Isolation - bascule d'édition";
        String aliceItem = addEdition("alice", title, "Pocket");
        String bobItem = addEdition("bob", title, "Gallimard");
        String bobEdition = bookField("bob", bobItem, "editionId");

        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("{ \"editionId\": \"" + bobEdition + "\" }")
                .when().put("/api/library/" + aliceItem + "/edition")
                .then().statusCode(404);

        // Alice's copy still points at the edition she recorded.
        assertEquals("Pocket", bookField("alice", aliceItem, "publisher"),
                "Bob must not have moved Alice's copy");
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

    // ── Upcoming releases ─────────────────────────────────────────────────────

    /** Stores an announcement on a series, the way the refresher writes one. */
    private String announce(String seriesId, int volume) {
        return QuarkusTransaction.requiringNew().call(() -> {
            UpcomingRelease release = new UpcomingRelease();
            release.series = series.findById(UUID.fromString(seriesId));
            release.volumeNumber = volume;
            release.releaseDate = LocalDate.now().plusMonths(3);
            release.datePrecision = DatePrecision.DAY;
            release.region = ReleaseRegion.FR;
            release.source = UpcomingRelease.SOURCE_MANUAL;
            release.confidence = ReleaseConfidence.CONFIRMED;
            release.updatedAt = OffsetDateTime.now();
            releases.persist(release);
            return release.id.toString();
        });
    }

    /**
     * An announcement is catalog data — it says what comes out, never who is waiting for
     * it. What must stay private is the perimeter: the series a user owns, has a wish on,
     * or follows. A run only Alice collects therefore announces nothing to Bob, even though
     * the row itself belongs to nobody.
     */
    @Test
    void upcomingReleasesOnlyCoverTheCallersOwnSeries() {
        String title = "Isolation - sorties alice";
        addSeriesVolume("alice", title, 1);
        String announced = announce(seriesId("alice", title), 2);

        given().auth().oauth2(token("alice")).queryParam("limit", 50)
                .when().get("/api/releases/upcoming")
                .then().statusCode(200)
                .body("id", hasItem(announced));

        given().auth().oauth2(token("bob")).queryParam("limit", 50)
                .when().get("/api/releases/upcoming")
                .then().statusCode(200)
                .body("id", not(hasItem(announced)));
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

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * The endpoint with the most to lose: an export is an entire library in one response, so
     * a filter forgotten here does not leak a row, it leaks everything at once. Checked on
     * both formats — the CSV goes through a different writer.
     */
    @Test
    void anExportNeverContainsAnotherUsersLibrary() {
        addLibraryItem("alice", "Isolation - exported title", "READ");
        addWishlistItem("alice", "Isolation - exported wish");

        given().auth().oauth2(token("alice")).queryParam("format", "json")
                .when().get("/api/export")
                .then().statusCode(200)
                .body("collection.book.title", hasItem("Isolation - exported title"))
                .body("wishlist.book.title", hasItem("Isolation - exported wish"));

        given().auth().oauth2(token("bob")).queryParam("format", "json")
                .when().get("/api/export")
                .then().statusCode(200)
                .body("collection.book.title", not(hasItem("Isolation - exported title")))
                .body("wishlist.book.title", not(hasItem("Isolation - exported wish")));

        String bobCsv = new String(given().auth().oauth2(token("bob")).queryParam("format", "csv")
                .when().get("/api/export").then().statusCode(200).extract().asByteArray(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(bobCsv.contains("Isolation - exported title"),
                "Alice's collection leaked into Bob's CSV export");
        assertFalse(bobCsv.contains("Isolation - exported wish"),
                "Alice's wishlist leaked into Bob's CSV export");
    }

    /**
     * A deferred export is fetched by identifier, which makes it the one export endpoint a
     * caller could point at somebody else. Unknown identifiers answer 404 whoever asks — the
     * cross-user case, which needs the deferred path to be taken at all, is in
     * {@code ExportAsyncTest}.
     */
    @Test
    void anUnknownExportJobIsNotFound() {
        given().auth().oauth2(token("bob"))
                .when().get("/api/export/" + java.util.UUID.randomUUID())
                .then().statusCode(404);
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

    /**
     * Goal progress is built on the day a title was finished, so it only moves when the
     * caller themselves finishes something. Alice marking a book as read — which stamps
     * {@code finished_at} with today's date, inside the current year — must leave Bob's
     * figure exactly where it was.
     */
    @Test
    void goalProgressOnlyCountsOwnReadings() {
        int bobProgressBefore = given().auth().oauth2(token("bob"))
                .when().get("/api/stats")
                .then().statusCode(200)
                .extract().jsonPath().getInt("goalCurrent");

        String aliceItem = addLibraryItem("alice", "Isolation - goal progress", "OWNED");
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"percent\": 100, \"status\": \"READ\" }")
                .when().put("/api/library/" + aliceItem + "/progress")
                .then().statusCode(204);

        given().auth().oauth2(token("bob"))
                .when().get("/api/stats")
                .then().statusCode(200)
                .body("goalCurrent", is(bobProgressBefore));
    }

    /**
     * The timeline aggregates the caller's own readings and nobody else's — buckets, totals
     * and breakdowns alike. Alice finishing a book must leave Bob's year exactly as it was,
     * and her authors must not show up among his.
     */
    @Test
    void theTimelineOnlyCountsOwnReadings() {
        int bobBooksBefore = given().auth().oauth2(token("bob"))
                .when().get("/api/stats/timeline")
                .then().statusCode(200)
                .extract().jsonPath().getInt("books");

        String aliceItem = addLibraryItem("alice", "Isolation - timeline", "OWNED");
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"percent\": 100, \"status\": \"READ\" }")
                .when().put("/api/library/" + aliceItem + "/progress")
                .then().statusCode(204);

        given().auth().oauth2(token("alice"))
                .when().get("/api/stats/timeline")
                .then().statusCode(200)
                .body("byAuthor.label", hasItem("Isolation Test"));

        given().auth().oauth2(token("bob"))
                .when().get("/api/stats/timeline")
                .then().statusCode(200)
                .body("books", is(bobBooksBefore))
                .body("byAuthor.label", not(hasItem("Isolation Test")));
    }
}
