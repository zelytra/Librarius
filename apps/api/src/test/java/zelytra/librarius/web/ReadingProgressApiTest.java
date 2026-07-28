package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Reading progress: the page / percentage conversion, and the dates the status transitions
 * fill in.
 *
 * <p>The conversion is asserted through the API rather than on the service because the
 * point of doing it server-side is that every reader of the collection sees the same
 * figure: entering page 120 of a 300-page book must read 40 % on the detail screen and on
 * the home carousel alike.
 */
@QuarkusTest
class ReadingProgressApiTest {

    /** Page count of the fixtures, chosen so the percentages come out whole. */
    private static final int PAGE_COUNT = 300;

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String token() {
        return keycloak.getAccessToken("alice");
    }

    /** Adds a 300-page title to Alice's collection and returns its identifier. */
    private String addBook(String title, String status) {
        return given().auth().oauth2(token()).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "%s", "authors": "Progress Test",
                                    "pageCount": %d },
                          "status": "%s" }
                        """.formatted(title, PAGE_COUNT, status))
                .when().post("/api/library")
                .then().statusCode(201)
                .extract().path("id");
    }

    private ValidatableResponse putProgress(String itemId, String body) {
        return given().auth().oauth2(token()).contentType("application/json").body(body)
                .when().put("/api/library/" + itemId + "/progress")
                .then();
    }

    private ValidatableResponse item(String itemId) {
        return given().auth().oauth2(token())
                .when().get("/api/library/" + itemId)
                .then().statusCode(200);
    }

    // ── Page ↔ percentage ─────────────────────────────────────────────────────

    @Test
    void aPageNumberIsStoredAsAPercentageToo() {
        String itemId = addBook("Progress - page to percent", "READING");

        putProgress(itemId, "{ \"currentPage\": 120 }").statusCode(204);

        item(itemId)
                .body("progress.currentPage", is(120))
                .body("progress.percent", is(40));
    }

    @Test
    void aPercentageIsStoredAsAPageToo() {
        String itemId = addBook("Progress - percent to page", "READING");

        putProgress(itemId, "{ \"percent\": 50 }").statusCode(204);

        item(itemId)
                .body("progress.percent", is(50))
                .body("progress.currentPage", is(150));
    }

    /** An edition with no page count cannot be converted, and must not read as zero. */
    @Test
    void aPercentageIsLeftAloneWhenTheTotalIsUnknown() {
        String itemId = given().auth().oauth2(token()).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "Progress - no page count",
                                    "authors": "Progress Test" },
                          "status": "READING" }
                        """)
                .when().post("/api/library")
                .then().statusCode(201).extract().path("id");

        putProgress(itemId, "{ \"percent\": 60 }").statusCode(204);

        item(itemId)
                .body("progress.percent", is(60))
                .body("progress.currentPage", nullValue());
    }

    /** The bounds of the column are the bounds of the payload: 150 % is a 400, not a row. */
    @Test
    void anOutOfRangePercentageIsRejected() {
        String itemId = addBook("Progress - out of range", "READING");

        putProgress(itemId, "{ \"percent\": 150 }").statusCode(400);
        putProgress(itemId, "{ \"currentPage\": -1 }").statusCode(400);
    }

    // ── Status transitions ────────────────────────────────────────────────────

    @Test
    void startingAReadingStampsTheStartDate() {
        String itemId = addBook("Progress - start date", "OWNED");

        putProgress(itemId, "{ \"status\": \"READING\" }").statusCode(204);

        item(itemId)
                .body("status", is("READING"))
                .body("progress.startedAt", is(LocalDate.now().toString()))
                .body("progress.finishedAt", nullValue());
    }

    /** A date the user typed wins over the default: the book may have been opened last year. */
    @Test
    void anExplicitStartDateIsKept() {
        String itemId = addBook("Progress - explicit start", "OWNED");

        putProgress(itemId, "{ \"status\": \"READING\", \"startedAt\": \"2020-03-04\" }")
                .statusCode(204);

        item(itemId).body("progress.startedAt", is("2020-03-04"));

        // Re-sending the position keeps it: only an empty start date is filled in.
        putProgress(itemId, "{ \"status\": \"READING\", \"startedAt\": \"2020-03-04\","
                + " \"currentPage\": 30 }").statusCode(204);

        item(itemId)
                .body("progress.startedAt", is("2020-03-04"))
                .body("progress.percent", is(10));
    }

    @Test
    void finishingABookCompletesTheProgress() {
        String itemId = addBook("Progress - finished", "READING");
        putProgress(itemId, "{ \"currentPage\": 120 }").statusCode(204);

        putProgress(itemId, "{ \"status\": \"READ\" }").statusCode(204);

        item(itemId)
                .body("status", is("READ"))
                .body("progress.percent", is(100))
                .body("progress.currentPage", is(PAGE_COUNT))
                .body("progress.finishedAt", is(LocalDate.now().toString()));
    }

    /** Marking a book read on a date of one's own must not be overwritten by today. */
    @Test
    void anExplicitFinishDateIsKept() {
        String itemId = addBook("Progress - explicit finish", "READING");

        putProgress(itemId, "{ \"status\": \"READ\", \"finishedAt\": \"2019-12-31\" }")
                .statusCode(204);

        item(itemId)
                .body("progress.finishedAt", is("2019-12-31"))
                .body("progress.percent", is(100));
    }

    // ── Exposure ──────────────────────────────────────────────────────────────

    /**
     * The home carousel reads the paginated collection, not the detail endpoint: the
     * progress has to travel with the list as well.
     */
    @Test
    void theProgressTravelsWithTheCollection() {
        String itemId = addBook("Progress - in the list", "READING");
        putProgress(itemId, "{ \"currentPage\": 150 }").statusCode(204);

        given().auth().oauth2(token()).queryParam("status", "READING")
                .queryParam("q", "Progress - in the list")
                .when().get("/api/library")
                .then().statusCode(200)
                .body("items[0].progress.percent", is(50))
                .body("items[0].progress.currentPage", is(150));
    }

    /** A title nobody has opened carries no progress at all, rather than a row of zeros. */
    @Test
    void anUntouchedTitleHasNoProgress() {
        String itemId = addBook("Progress - untouched", "OWNED");

        item(itemId)
                .body("id", notNullValue())
                .body("progress", nullValue());
    }
}
