package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Right to portability (GDPR art. 20): {@code GET /api/export}.
 *
 * <p>Covers the synchronous path, which is the one every real library takes; the deferred one
 * is in {@code ExportAsyncTest}, and the round trip in {@code ExportRoundTripTest}.
 */
@QuarkusTest
class ExportApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    private void addTitle(String user, String title, String status) {
        given().auth().oauth2(token(user)).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "%s", "authors": "Export Test",
                                    "genres": "Récit" },
                          "status": "%s", "rating": 3 }
                        """.formatted(title, status))
                .when().post("/api/library").then().statusCode(201);
    }

    // ── JSON ──────────────────────────────────────────────────────────────────

    @Test
    void theJsonExportCarriesEverythingTheUserEntered() {
        addTitle("alice", "Export - json", "READ");
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("""
                        { "book": { "kind": "MANGA", "title": "Export - wish", "authors": "E" },
                          "priority": "SOON", "note": "à surveiller" }
                        """)
                .when().post("/api/wishlist").then().statusCode(201);

        given().auth().oauth2(token("alice")).queryParam("format", "json")
                .when().get("/api/export")
                .then().statusCode(200)
                .header("Content-Disposition", containsString("attachment"))
                .header("Content-Disposition", containsString(".json"))
                .body("schemaVersion", is(1))
                .body("user.id", not(org.hamcrest.Matchers.emptyOrNullString()))
                .body("collection.book.title", hasItem("Export - json"))
                .body("wishlist.book.title", hasItem("Export - wish"))
                .body("wishlist.note", hasItem("à surveiller"));
    }

    /** JSON is the default: a user who asks for "their data" gets the complete archive. */
    @Test
    void theFormatDefaultsToJson() {
        given().auth().oauth2(token("alice"))
                .when().get("/api/export")
                .then().statusCode(200)
                .body("schemaVersion", is(1));
    }

    @Test
    void anUnknownFormatIsRejected() {
        given().auth().oauth2(token("alice")).queryParam("format", "xml")
                .when().get("/api/export")
                .then().statusCode(400);
    }

    // ── CSV ───────────────────────────────────────────────────────────────────

    /**
     * The two things the CSV has to survive to be worth anything: a spreadsheet opening it,
     * and a title carrying the separator.
     */
    @Test
    void theCsvExportOpensInASpreadsheet() {
        addTitle("bob", "Export ; l'été à Naïs", "READING");
        String itemId = given().auth().oauth2(token("bob")).queryParam("q", "Naïs")
                .when().get("/api/library").then().statusCode(200)
                .extract().path("items[0].id");
        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("{ \"rating\": 5, \"review\": \"Une \\\"merveille\\\" ; à relire.\" }")
                .when().put("/api/library/" + itemId + "/review").then().statusCode(200);

        byte[] body = given().auth().oauth2(token("bob")).queryParam("format", "csv")
                .when().get("/api/export")
                .then().statusCode(200)
                .header("Content-Type", containsString("text/csv"))
                .header("Content-Disposition", containsString(".csv"))
                .extract().asByteArray();

        // UTF-8 byte-order mark, without which Excel reads the accents as Latin-1.
        assertEquals((byte) 0xEF, body[0]);
        assertEquals((byte) 0xBB, body[1]);
        assertEquals((byte) 0xBF, body[2]);

        String csv = new String(body, StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");
        assertTrue(lines[0].endsWith("Title;Author;ISBN13;Publisher;Number of Pages;"
                        + "Original Publication Year;My Rating;My Review;Exclusive Shelf;"
                        + "Bookshelves;Date Added;Date Read;Private Notes;Librarius Kind;"
                        + "Librarius Series;Librarius Volume;Librarius Rank;Librarius Priority;"
                        + "Librarius Estimated Price;Librarius Progress Percent;"
                        + "Librarius Current Page"),
                "unexpected header: " + lines[0]);

        // The accents survive, and the semicolon inside the title stays inside its cell.
        assertTrue(csv.contains("\"Export ; l'été à Naïs\""), "unquoted or mangled title:\n" + csv);
        assertTrue(csv.contains(";currently-reading;collection;"),
                "the reading status must be written in the Goodreads vocabulary:\n" + csv);
        // The private review, with its own quotes doubled as RFC 4180 wants them.
        assertTrue(csv.contains("\"Une \"\"merveille\"\" ; à relire.\""),
                "the review is missing or its quoting is broken:\n" + csv);
    }

    /** A wish is a book the user does not own: it lands on the `to-read` shelf. */
    @Test
    void theCsvExportCarriesTheWishlistToo() {
        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "Export - csv wish",
                                    "authors": "Export Test" },
                          "priority": "SOMEDAY", "estimatedPrice": 12.00 }
                        """)
                .when().post("/api/wishlist").then().statusCode(201);

        String csv = new String(given().auth().oauth2(token("bob")).queryParam("format", "csv")
                .when().get("/api/export").then().statusCode(200).extract().asByteArray(),
                StandardCharsets.UTF_8);

        assertTrue(csv.contains("Export - csv wish;Export Test;;;;;;;to-read;wishlist;"),
                "the wish is missing or misshapen:\n" + csv);
        assertTrue(csv.contains(";SOMEDAY;12.00;;"), "the priority and price are missing:\n" + csv);
    }

    // ── Isolation ─────────────────────────────────────────────────────────────

    /**
     * The worst thing this endpoint could do. An export is a whole library in one response:
     * a missing {@code where user_id = …} here leaks everything at once.
     */
    @Test
    void anExportOnlyEverContainsItsOwnAuthorsData() {
        addTitle("alice", "Export - alice only", "OWNED");

        given().auth().oauth2(token("bob")).queryParam("format", "json")
                .when().get("/api/export")
                .then().statusCode(200)
                .body("collection.book.title", not(hasItem("Export - alice only")));

        String csv = new String(given().auth().oauth2(token("bob")).queryParam("format", "csv")
                .when().get("/api/export").then().statusCode(200).extract().asByteArray(),
                StandardCharsets.UTF_8);
        assertTrue(!csv.contains("Export - alice only"), "Alice's title leaked into Bob's CSV");
    }

    @Test
    void exportingRequiresAToken() {
        given().when().get("/api/export").then().statusCode(401);
        given().when().get("/api/export/" + java.util.UUID.randomUUID()).then().statusCode(401);
    }
}
