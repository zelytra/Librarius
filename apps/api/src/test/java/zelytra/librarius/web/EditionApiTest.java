package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Alternate editions of a work, and moving an owned title from one to another.
 *
 * <p>Each test uses a title of its own: the catalog is shared by the whole suite, and works
 * are now matched on (kind, title, authors, volume), so two tests sharing a title would
 * share the editions gathered under it.
 */
@QuarkusTest
class EditionApiTest {

    private static final String AUTHOR = "Edition Test";

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    /**
     * Adds a title to a user's collection and returns its identifier. Two calls with the
     * same title and a different publisher are two editions of one work.
     */
    private String add(String user, String title, String publisher, Integer pageCount) {
        return given().auth().oauth2(token(user)).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "%s", "authors": "%s",
                                    "publisher": "%s", "pageCount": %s },
                          "status": "OWNED" }
                        """.formatted(title, AUTHOR, publisher,
                        pageCount == null ? "null" : pageCount.toString()))
                .when().post("/api/library")
                .then().statusCode(201)
                .extract().path("id");
    }

    private Response item(String user, String itemId) {
        return given().auth().oauth2(token(user))
                .when().get("/api/library/" + itemId)
                .then().statusCode(200)
                .extract().response();
    }

    /** The editions the work of an owned title exists in. */
    private Response editionsOf(String user, String itemId) {
        String workId = item(user, itemId).path("book.workId");
        return given().auth().oauth2(token(user))
                .when().get("/api/works/" + workId + "/editions")
                .then().statusCode(200)
                .extract().response();
    }

    private Response switchTo(String user, String itemId, String editionId) {
        return given().auth().oauth2(token(user)).contentType("application/json")
                .body("{ \"editionId\": \"" + editionId + "\" }")
                .when().put("/api/library/" + itemId + "/edition")
                .extract().response();
    }

    // ── Listing ───────────────────────────────────────────────────────────────

    /**
     * The whole feature rests on this: two entries describing the same title must gather
     * under one work, or the schema's one-work-to-many-editions split never materialises and
     * there is never anything to list.
     */
    @Test
    void twoEntriesForTheSameTitleGatherUnderOneWork() {
        String title = "Éditions - même œuvre";
        String pocket = add("alice", title, "Pocket", 512);
        String hardcover = add("alice", title, "Robert Laffont", 640);

        assertEquals(item("alice", pocket).path("book.workId"),
                item("alice", hardcover).path("book.workId"),
                "the two entries describe the same work");
        assertNotEquals(item("alice", pocket).path("book.editionId"),
                item("alice", hardcover).path("book.editionId"),
                "…in two different editions");

        editionsOf("alice", pocket).then()
                .body("$", hasSize(2))
                .body("publisher", hasItem("Pocket"))
                .body("publisher", hasItem("Robert Laffont"))
                // Alice owns both, so neither is offered as a switch target.
                .body("owned", not(hasItem(false)));
    }

    /** A title nobody has entered twice knows a single edition: nothing to compare. */
    @Test
    void aWorkKnownInOneEditionListsThatOneAlone() {
        String only = add("alice", "Éditions - édition unique", "Folio", 300);

        editionsOf("alice", only).then()
                .body("$", hasSize(1))
                .body("[0].publisher", is("Folio"))
                .body("[0].owned", is(true))
                .body("[0].id", is(item("alice", only).path("book.editionId")));
    }

    /** An unknown work is not a 500 and not an empty list: it is an absence. */
    @Test
    void anUnknownWorkIsNotFound() {
        given().auth().oauth2(token("alice"))
                .when().get("/api/works/" + UUID.randomUUID() + "/editions")
                .then().statusCode(404);
    }

    // ── Switching ─────────────────────────────────────────────────────────────

    /**
     * Everything the ownership row records about the reader describes the reader and not the
     * object: buying the hardcover does not un-read a book, nor cancel its rating.
     */
    @Test
    void switchingEditionKeepsWhatDescribesTheReader() {
        String title = "Éditions - ce qui reste";
        String item = add("alice", title, "Pocket", 300);
        // Wished rather than owned: the target of a switch has to be an edition the user
        // does not already have, which is exactly what a wish is.
        String targetId = given().auth().oauth2(token("alice")).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "%s", "authors": "%s",
                                    "publisher": "Le Livre de Poche", "pageCount": 300 },
                          "priority": "SOON" }
                        """.formatted(title, AUTHOR))
                .when().post("/api/wishlist")
                .then().statusCode(201)
                .extract().path("book.editionId");

        String orId = given().auth().oauth2(token("alice")).when().get("/api/categories")
                .then().statusCode(200).extract().path("find { it.code == 'or' }.id");
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"categoryId\": \"" + orId + "\" }")
                .when().put("/api/library/" + item + "/rank").then().statusCode(200);
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"rating\": 5, \"review\": \"Relu trois fois.\" }")
                .when().put("/api/library/" + item + "/review").then().statusCode(200);
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("""
                        { "currentPage": 150, "status": "READING", "startedAt": "2026-02-01" }
                        """)
                .when().put("/api/library/" + item + "/progress").then().statusCode(204);

        switchTo("alice", item, targetId).then().statusCode(200)
                .body("book.editionId", is(targetId))
                .body("book.publisher", is("Le Livre de Poche"))
                .body("status", is("READING"))
                .body("rating", is(5))
                .body("review", is("Relu trois fois."))
                .body("rankCode", is("or"))
                .body("progress.startedAt", is("2026-02-01"));
    }

    /**
     * The reading position is the one thing a change of edition cannot leave alone: page 150
     * of a 300-page paperback is not page 150 of a 600-page hardcover. The percentage is what
     * carries over, and the page is recomputed from the new page count.
     */
    @Test
    void switchingEditionReanchorsThePositionOnThePercentage() {
        String title = "Éditions - repère de lecture";
        String item = add("alice", title, "Pocket", 300);
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"currentPage\": 150, \"status\": \"READING\" }")
                .when().put("/api/library/" + item + "/progress").then().statusCode(204);
        item("alice", item).then()
                .body("progress.percent", is(50))
                .body("progress.currentPage", is(150));

        String bigger = given().auth().oauth2(token("alice")).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "%s", "authors": "%s",
                                    "publisher": "Omnibus", "pageCount": 600 },
                          "priority": "SOON" }
                        """.formatted(title, AUTHOR))
                .when().post("/api/wishlist")
                .then().statusCode(201)
                .extract().path("book.editionId");

        switchTo("alice", item, bigger).then().statusCode(200)
                .body("progress.percent", is(50))
                .body("progress.currentPage", is(300));
    }

    /** Switching onto the edition already in force is a no-op, not a refusal. */
    @Test
    void switchingOntoTheCurrentEditionChangesNothing() {
        String item = add("alice", "Éditions - même cible", "Pocket", 200);
        String current = item("alice", item).path("book.editionId");

        switchTo("alice", item, current).then().statusCode(200)
                .body("book.editionId", is(current));
    }

    /**
     * {@code UNIQUE(user_id, edition_id)} forbids owning the same edition twice, so the
     * refusal is a 409 carrying a message the screen can show — not a constraint violation
     * surfacing as a 500.
     */
    @Test
    void switchingToAnEditionAlreadyOwnedIsRefused() {
        String title = "Éditions - déjà possédée";
        String first = add("alice", title, "Pocket", 200);
        String second = add("alice", title, "Gallimard", 220);
        String secondEdition = item("alice", second).path("book.editionId");

        switchTo("alice", first, secondEdition).then().statusCode(409)
                .body("message", notNullValue());

        // Neither item moved.
        item("alice", first).then().body("book.publisher", is("Pocket"));
        item("alice", second).then().body("book.publisher", is("Gallimard"));
    }

    /** An edition of another title is a malformed request, not a hidden resource. */
    @Test
    void switchingToAnEditionOfAnotherWorkIsRefused() {
        String item = add("alice", "Éditions - œuvre A", "Pocket", 200);
        String other = add("alice", "Éditions - œuvre B", "Pocket", 200);
        String foreignEdition = item("alice", other).path("book.editionId");

        switchTo("alice", item, foreignEdition).then().statusCode(400);
        item("alice", item).then().body("book.title", is("Éditions - œuvre A"));
    }

    @Test
    void switchingToAnUnknownEditionIsRefused() {
        String item = add("alice", "Éditions - cible inconnue", "Pocket", 200);

        switchTo("alice", item, UUID.randomUUID().toString()).then().statusCode(400);
    }

    /** The whole point of the feature: correcting the edition on the shelf. */
    @Test
    void theCollectionShowsTheEditionTheUserSwitchedTo() {
        String title = "Éditions - correction";
        String item = add("alice", title, "Pocket", 250);
        String target = given().auth().oauth2(token("alice")).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "%s", "authors": "%s",
                                    "publisher": "Bragelonne", "pageCount": 400,
                                    "isbn13": "9791234567890" },
                          "priority": "SOON" }
                        """.formatted(title, AUTHOR))
                .when().post("/api/wishlist")
                .then().statusCode(201)
                .extract().path("book.editionId");

        switchTo("alice", item, target).then().statusCode(200);

        given().auth().oauth2(token("alice")).queryParam("q", title)
                .when().get("/api/library")
                .then().statusCode(200)
                .body("items.find { it.id == '" + item + "' }.book.publisher", is("Bragelonne"))
                .body("items.find { it.id == '" + item + "' }.book.pageCount", is(400))
                .body("items.find { it.id == '" + item + "' }.book.isbn13", is("9791234567890"));
    }
}
