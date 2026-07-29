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

    // ── Abandoning ────────────────────────────────────────────────────────────

    /**
     * The whole point of the fourth status: a book given up at page 120 was read up to page
     * 120. The date is stamped like a finish date — the day tracking stopped is worth
     * keeping — but the position is left exactly where the reader got to. Completing it the
     * way {@code READ} does would destroy the only thing this status has to record.
     */
    @Test
    void givingUpOnATitleStampsTheDateAndKeepsThePosition() {
        String itemId = addBook("Progress - given up", "READING");
        putProgress(itemId, "{ \"currentPage\": 120 }").statusCode(204);

        putProgress(itemId, "{ \"status\": \"ABANDONED\", \"currentPage\": 120 }").statusCode(204);

        item(itemId)
                .body("status", is("ABANDONED"))
                .body("progress.currentPage", is(120))
                .body("progress.percent", is(40))
                .body("progress.finishedAt", is(LocalDate.now().toString()));
    }

    /** A date the user typed wins here as well: the book may have been dropped last year. */
    @Test
    void anExplicitAbandonDateIsKept() {
        String itemId = addBook("Progress - explicit abandon date", "READING");

        putProgress(itemId, "{ \"status\": \"ABANDONED\", \"finishedAt\": \"2021-06-01\","
                + " \"percent\": 15 }").statusCode(204);

        item(itemId)
                .body("progress.finishedAt", is("2021-06-01"))
                .body("progress.percent", is(15));
    }

    /**
     * Picking a book up again is a normal thing to do, and nothing special-cases against it:
     * {@code ABANDONED} → {@code READING} is the ordinary transition, and it clears the date
     * the abandonment left behind.
     */
    @Test
    void anAbandonedTitleCanBePickedUpAgain() {
        String itemId = addBook("Progress - picked up again", "READING");
        putProgress(itemId, "{ \"status\": \"ABANDONED\", \"startedAt\": \"2024-02-02\","
                + " \"currentPage\": 90 }").statusCode(204);

        putProgress(itemId, "{ \"status\": \"READING\", \"startedAt\": \"2024-02-02\","
                + " \"currentPage\": 90 }").statusCode(204);

        item(itemId)
                .body("status", is("READING"))
                .body("progress.startedAt", is("2024-02-02"))
                .body("progress.currentPage", is(90))
                .body("progress.finishedAt", nullValue());
    }

    /** And it can be finished after all, which completes the position the normal way. */
    @Test
    void anAbandonedTitleCanStillBeFinished() {
        String itemId = addBook("Progress - finished after all", "READING");
        putProgress(itemId, "{ \"status\": \"ABANDONED\", \"currentPage\": 60 }").statusCode(204);

        putProgress(itemId, "{ \"status\": \"READ\" }").statusCode(204);

        item(itemId)
                .body("status", is("READ"))
                .body("progress.percent", is(100))
                .body("progress.currentPage", is(PAGE_COUNT));
    }

    /** A filter value of its own: an abandoned title falls into none of the three others. */
    @Test
    void abandonedTitlesAreFilteredOnLikeAnyOtherStatus() {
        String itemId = addBook("Progress - filtered abandoned", "READING");
        putProgress(itemId, "{ \"status\": \"ABANDONED\" }").statusCode(204);

        given().auth().oauth2(token()).queryParam("status", "ABANDONED")
                .queryParam("q", "Progress - filtered abandoned")
                .when().get("/api/library")
                .then().statusCode(200)
                .body("total", is(1))
                .body("items[0].id", is(itemId));

        for (String other : new String[] {"OWNED", "READING", "READ"}) {
            given().auth().oauth2(token()).queryParam("status", other)
                    .queryParam("q", "Progress - filtered abandoned")
                    .when().get("/api/library")
                    .then().statusCode(200)
                    .body("total", is(0));
        }
    }

    /** It counts in the abandoned counter of {@code /api/stats}, and in no other. */
    @Test
    void abandonedTitlesHaveTheirOwnCounter() {
        var before = given().auth().oauth2(token()).when().get("/api/stats")
                .then().statusCode(200).extract().jsonPath();
        int abandoned = before.getInt("abandoned");
        int read = before.getInt("read");
        int reading = before.getInt("reading");
        int toRead = before.getInt("toRead");
        int pagesRead = before.getInt("pagesRead");

        putProgress(addBook("Progress - counted apart", "READING"), "{ \"status\": \"ABANDONED\" }")
                .statusCode(204);

        given().auth().oauth2(token()).when().get("/api/stats")
                .then().statusCode(200)
                .body("abandoned", is(abandoned + 1))
                // The fixture was added as READING and left it again: the three other
                // counters are exactly where they were.
                .body("read", is(read))
                .body("reading", is(reading))
                .body("toRead", is(toRead))
                // The 300 pages of the fixture are not read pages.
                .body("pagesRead", is(pagesRead));
    }

    /**
     * Another user's item is unknown, not forbidden — a 403 would confirm that it exists.
     * Asserted on this transition rather than trusted from the endpoint's other tests: the
     * status is new, and an ownership check is exactly what a new branch gets written around.
     */
    @Test
    void anotherUsersTitleCannotBeAbandoned() {
        String itemId = addBook("Progress - not bob's to abandon", "READING");
        putProgress(itemId, "{ \"currentPage\": 30 }").statusCode(204);

        given().auth().oauth2(keycloak.getAccessToken("bob")).contentType("application/json")
                .body("{ \"status\": \"ABANDONED\" }")
                .when().put("/api/library/" + itemId + "/progress")
                .then().statusCode(404);

        // And Alice's title is untouched: not abandoned, and still at page 30.
        item(itemId)
                .body("status", is("READING"))
                .body("progress.currentPage", is(30));
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
