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
 * are matched on (kind, title, authors, volume), so two tests sharing a title would share
 * the editions gathered under it.
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

    /**
     * Wishes for another edition of a title, and returns its identifier. A wish is the
     * cheapest way to put an edition in the catalog without owning it — which is exactly what
     * the target of a switch has to be.
     */
    private String wishEdition(String user, String title, String publisher, Integer pageCount,
            String isbn13) {
        return given().auth().oauth2(token(user)).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "%s", "authors": "%s",
                                    "publisher": "%s", "pageCount": %d, "isbn13": %s },
                          "priority": "SOON" }
                        """.formatted(title, AUTHOR, publisher, pageCount,
                        isbn13 == null ? "null" : "\"" + isbn13 + "\""))
                .when().post("/api/wishlist")
                .then().statusCode(201)
                .extract().path("book.editionId");
    }

    private Response item(String user, String itemId) {
        return given().auth().oauth2(token(user))
                .when().get("/api/library/" + itemId)
                .then().statusCode(200)
                .extract().response();
    }

    /** One field of the book behind an owned title, e.g. {@code workId}. */
    private String bookField(String user, String itemId, String field) {
        return item(user, itemId).path("book." + field);
    }

    /** The editions the work of an owned title exists in. */
    private Response editionsOf(String user, String itemId) {
        return given().auth().oauth2(token(user))
                .when().get("/api/works/" + bookField(user, itemId, "workId") + "/editions")
                .then().statusCode(200)
                .extract().response();
    }

    private Response switchTo(String user, String itemId, String editionId) {
        return given().auth().oauth2(token(user)).contentType("application/json")
                .body("{ \"editionId\": \"" + editionId + "\" }")
                .when().put("/api/library/" + itemId + "/edition");
    }

    // ── Listing ───────────────────────────────────────────────────────────────

    /**
     * The whole feature rests on this: two entries describing the same title must gather
     * under one work, or the one-work-to-many-editions split of the schema never
     * materialises and there is never anything to list.
     */
    @Test
    void twoEntriesForTheSameTitleGatherUnderOneWork() {
        String title = "Éditions - même œuvre";
        String pocket = add("alice", title, "Pocket", 512);
        String hardcover = add("alice", title, "Robert Laffont", 640);

        assertEquals(bookField("alice", pocket, "workId"),
                bookField("alice", hardcover, "workId"),
                "the two entries describe the same work");
        assertNotEquals(bookField("alice", pocket, "editionId"),
                bookField("alice", hardcover, "editionId"),
                "…in two different editions");

        editionsOf("alice", pocket).then()
                .body("$", hasSize(2))
                .body("publisher", hasItem("Pocket"))
                .body("publisher", hasItem("Robert Laffont"))
                // Alice owns both, so neither is offered as a switch target.
                .body("owned", not(hasItem(false)));
    }

    /**
     * The work is shared by everyone owning the title, so a second entry may **complete** it
     * and never contradict it: a thinner entry cannot wipe a synopsis, and a field nobody had
     * supplied is picked up from whoever finally does.
     */
    @Test
    void aSecondEntryCompletesTheWorkWithoutOverwritingIt() {
        String title = "Éditions - complétion";
        String first = given().auth().oauth2(token("alice")).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "%s", "authors": "%s",
                                    "publisher": "Pocket", "synopsis": "Le résumé complet.",
                                    "genres": "Fantasy" },
                          "status": "OWNED" }
                        """.formatted(title, AUTHOR))
                .when().post("/api/library").then().statusCode(201).extract().path("id");

        String second = given().auth().oauth2(token("alice")).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "%s", "authors": "%s",
                                    "publisher": "Gallimard", "synopsis": "Un pitch bâclé.",
                                    "originalYear": 1998 },
                          "status": "OWNED" }
                        """.formatted(title, AUTHOR))
                .when().post("/api/library").then().statusCode(201).extract().path("id");

        assertEquals(bookField("alice", first, "workId"), bookField("alice", second, "workId"));
        item("alice", second).then()
                .body("book.synopsis", is("Le résumé complet."))
                .body("book.genres", is("Fantasy"))
                // …and the year the first entry never supplied is now on the shared work.
                .body("book.originalYear", is(1998));
    }

    /** A title nobody has entered twice knows a single edition: nothing to compare. */
    @Test
    void aWorkKnownInOneEditionListsThatOneAlone() {
        String only = add("alice", "Éditions - édition unique", "Folio", 300);
        String editionId = bookField("alice", only, "editionId");

        editionsOf("alice", only).then()
                .body("$", hasSize(1))
                .body("[0].publisher", is("Folio"))
                .body("[0].pageCount", is(300))
                .body("[0].owned", is(true))
                .body("[0].id", is(editionId));
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
        String itemId = add("alice", title, "Pocket", 300);
        String target = wishEdition("alice", title, "Le Livre de Poche", 300, null);

        String orId = given().auth().oauth2(token("alice")).when().get("/api/categories")
                .then().statusCode(200).extract().path("find { it.code == 'or' }.id");
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"categoryId\": \"" + orId + "\" }")
                .when().put("/api/library/" + itemId + "/rank").then().statusCode(200);
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"rating\": 5, \"review\": \"Relu trois fois.\" }")
                .when().put("/api/library/" + itemId + "/review").then().statusCode(200);
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"currentPage\": 150, \"status\": \"READING\","
                        + " \"startedAt\": \"2026-02-01\" }")
                .when().put("/api/library/" + itemId + "/progress").then().statusCode(204);

        switchTo("alice", itemId, target).then().statusCode(200)
                .body("book.editionId", is(target))
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
        String itemId = add("alice", title, "Pocket", 300);
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"currentPage\": 150, \"status\": \"READING\" }")
                .when().put("/api/library/" + itemId + "/progress").then().statusCode(204);
        item("alice", itemId).then()
                .body("progress.percent", is(50))
                .body("progress.currentPage", is(150));

        String bigger = wishEdition("alice", title, "Omnibus", 600, null);

        switchTo("alice", itemId, bigger).then().statusCode(200)
                .body("progress.percent", is(50))
                .body("progress.currentPage", is(300));
    }

    /** Switching onto the edition already in force is a no-op, not a refusal. */
    @Test
    void switchingOntoTheCurrentEditionChangesNothing() {
        String itemId = add("alice", "Éditions - même cible", "Pocket", 200);
        String current = bookField("alice", itemId, "editionId");

        switchTo("alice", itemId, current).then().statusCode(200)
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
        String secondEdition = bookField("alice", second, "editionId");

        switchTo("alice", first, secondEdition).then().statusCode(409)
                .body("message", notNullValue());

        // Neither item moved.
        item("alice", first).then().body("book.publisher", is("Pocket"));
        item("alice", second).then().body("book.publisher", is("Gallimard"));
    }

    /** An edition of another title is a malformed request, not a hidden resource. */
    @Test
    void switchingToAnEditionOfAnotherWorkIsRefused() {
        String itemId = add("alice", "Éditions - œuvre A", "Pocket", 200);
        String other = add("alice", "Éditions - œuvre B", "Pocket", 200);
        String foreignEdition = bookField("alice", other, "editionId");

        switchTo("alice", itemId, foreignEdition).then().statusCode(400);
        item("alice", itemId).then().body("book.title", is("Éditions - œuvre A"));
    }

    @Test
    void switchingToAnUnknownEditionIsRefused() {
        String itemId = add("alice", "Éditions - cible inconnue", "Pocket", 200);

        switchTo("alice", itemId, UUID.randomUUID().toString()).then().statusCode(400);
    }

    /** The whole point of the feature: correcting the edition on the shelf. */
    @Test
    void theCollectionShowsTheEditionTheUserSwitchedTo() {
        String title = "Éditions - correction";
        String itemId = add("alice", title, "Pocket", 250);
        String target = wishEdition("alice", title, "Bragelonne", 400, "9791234567890");

        switchTo("alice", itemId, target).then().statusCode(200);

        String found = "items.find { it.id == '" + itemId + "' }.book.";
        given().auth().oauth2(token("alice")).queryParam("q", title)
                .when().get("/api/library")
                .then().statusCode(200)
                .body(found + "publisher", is("Bragelonne"))
                .body(found + "pageCount", is(400))
                .body(found + "isbn13", is("9791234567890"));
    }
}
