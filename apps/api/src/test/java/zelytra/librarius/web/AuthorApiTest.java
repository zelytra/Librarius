package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * The {@code /api/authors} resource: name search, bibliography and follow.
 *
 * <p>An author is shared catalog data keyed on the fold of their name, so each test credits
 * its own uniquely named author: two tests reusing a name would land on the same row and
 * interfere. Authors come into being through {@code POST /api/library}, whose credit line
 * {@code AuthorService} resolves into {@code author} rows exactly as the backfill did.
 */
@QuarkusTest
class AuthorApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    /** Adds a book crediting an author, which creates the shared {@code author} row. */
    private void addBook(String user, String title, String author, String coverUrl) {
        given().auth().oauth2(token(user)).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "%s", "authors": "%s",
                                    "coverUrl": "%s" },
                          "status": "OWNED" }
                        """.formatted(title, author, coverUrl))
                .when().post("/api/library")
                .then().statusCode(201);
    }

    /** Identifier of an author, read back from the name search. */
    private String authorId(String user, String name) {
        return given().auth().oauth2(token(user)).queryParam("q", name)
                .when().get("/api/authors")
                .then().statusCode(200)
                .extract().path("find { it.name == '" + name + "' }.id");
    }

    // ── Search and bibliography ───────────────────────────────────────────────

    /**
     * The acceptance case: an author can be opened by id and their bibliography lists every
     * work reachable through {@code work_author}, cover and all.
     */
    @Test
    void searchFindsAnAuthorAndTheDetailListsTheirBibliography() {
        String author = "Author Test - Ursula " + UUID.randomUUID();
        String cover = "https://covers.example/le-guin.jpg";
        addBook("alice", "Author Test - Left Hand", author, cover);
        addBook("alice", "Author Test - Dispossessed", author, "");

        // The name search finds the author, with a work count and no follow yet.
        given().auth().oauth2(token("alice")).queryParam("q", "Author Test - Ursula")
                .when().get("/api/authors")
                .then().statusCode(200)
                .body("find { it.name == '" + author + "' }.workCount", is(2))
                .body("find { it.name == '" + author + "' }.followed", is(false));

        String id = authorId("alice", author);

        given().auth().oauth2(token("alice"))
                .when().get("/api/authors/" + id)
                .then().statusCode(200)
                .body("name", is(author))
                .body("followed", is(false))
                .body("works.size()", is(2))
                .body("works.title", hasItem("Author Test - Left Hand"))
                .body("works.find { it.title == 'Author Test - Left Hand' }.coverUrl", is(cover))
                .body("works.find { it.title == 'Author Test - Left Hand' }.workId",
                        notNullValue());
    }

    /** A blank search term returns nothing rather than the whole catalog. */
    @Test
    void aBlankSearchReturnsNothing() {
        given().auth().oauth2(token("alice")).queryParam("q", "  ")
                .when().get("/api/authors")
                .then().statusCode(200)
                .body("size()", is(0));

        given().auth().oauth2(token("alice"))
                .when().get("/api/authors")
                .then().statusCode(200)
                .body("size()", is(0));
    }

    // ── Follow ────────────────────────────────────────────────────────────────

    /** Following and unfollowing are both idempotent: a retried request must not fail. */
    @Test
    void followingIsIdempotent() {
        String author = "Author Test - Follow " + UUID.randomUUID();
        addBook("alice", "Author Test - Follow work", author, "");
        String id = authorId("alice", author);

        given().auth().oauth2(token("alice")).when().put("/api/authors/" + id + "/follow")
                .then().statusCode(204);
        given().auth().oauth2(token("alice")).when().put("/api/authors/" + id + "/follow")
                .then().statusCode(204);

        given().auth().oauth2(token("alice"))
                .when().get("/api/authors/" + id)
                .then().statusCode(200)
                .body("followed", is(true));

        given().auth().oauth2(token("alice")).when().delete("/api/authors/" + id + "/follow")
                .then().statusCode(204);
        given().auth().oauth2(token("alice")).when().delete("/api/authors/" + id + "/follow")
                .then().statusCode(204);

        given().auth().oauth2(token("alice"))
                .when().get("/api/authors/" + id)
                .then().statusCode(200)
                .body("followed", is(false));
    }

    /**
     * The standing rule since #39: one user's follow state is never visible to another. It
     * also pins the catalog-browser scope — Bob opens an author he owns nothing of and gets a
     * 200, not the 404 a series would answer.
     */
    @Test
    void aFollowIsNeverVisibleToAnotherUser() {
        String author = "Author Test - Isolation " + UUID.randomUUID();
        addBook("alice", "Author Test - Isolation work", author, "");
        String id = authorId("alice", author);

        given().auth().oauth2(token("alice")).when().put("/api/authors/" + id + "/follow")
                .then().statusCode(204);

        // Alice follows; her own detail says so.
        given().auth().oauth2(token("alice"))
                .when().get("/api/authors/" + id)
                .then().statusCode(200)
                .body("followed", is(true));

        // Bob owns nothing of this author, yet the catalog row is his to open — and Alice's
        // follow never leaks into his view of it.
        given().auth().oauth2(token("bob"))
                .when().get("/api/authors/" + id)
                .then().statusCode(200)
                .body("name", is(author))
                .body("followed", is(false));

        // Bob's own search of the same author reports no follow either.
        given().auth().oauth2(token("bob")).queryParam("q", "Author Test - Isolation")
                .when().get("/api/authors")
                .then().statusCode(200)
                .body("find { it.id == '" + id + "' }.followed", is(false));
    }

    // ── Unknown identifiers ───────────────────────────────────────────────────

    @Test
    void anUnknownAuthorAnswersNotFound() {
        String unknown = UUID.randomUUID().toString();

        given().auth().oauth2(token("alice")).when().get("/api/authors/" + unknown)
                .then().statusCode(404);
        given().auth().oauth2(token("alice")).when().put("/api/authors/" + unknown + "/follow")
                .then().statusCode(404);
        given().auth().oauth2(token("alice")).when().delete("/api/authors/" + unknown + "/follow")
                .then().statusCode(404);
    }
}
