package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zelytra.librarius.contribution.FakeStorePageFetcher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static zelytra.librarius.web.SocialTestSupport.token;

/**
 * Member edit proposals on the shared catalog (#618), their review by an admin, the sale links
 * of an edition (#619) and the automatic check against a store page (#620). alice is the admin;
 * bob and carol are members.
 */
@QuarkusTest
class CatalogEditApiTest {

    @Inject
    FakeStorePageFetcher stores;

    @BeforeEach
    @AfterEach
    void reset() {
        stores.reset();
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private static String randomIsbn() {
        return "978" + String.format("%010d", ThreadLocalRandom.current().nextLong(10_000_000_000L));
    }

    /** Adds a book for {@code user} and answers its library item. */
    private static String addBook(String user, String title, String isbn) {
        return given().auth().oauth2(token(user)).contentType("application/json")
                .body(Map.of("book", Map.of("kind", "BOOK", "title", title, "authors", "Edit Test",
                        "publisher", "Old Press", "isbn13", isbn, "pageCount", 200), "status", "TO_READ"))
                .when().post("/api/library").then().statusCode(201).extract().path("id");
    }

    private static JsonPath book(String user, String itemId) {
        return given().auth().oauth2(token(user)).when().get("/api/library/" + itemId)
                .then().statusCode(200).extract().jsonPath();
    }

    private static String editionOf(String user, String itemId) {
        return book(user, itemId).getString("book.editionId");
    }

    private static Map<String, Object> proposal(String type, String target, Map<String, ?> fields, List<String> links) {
        Map<String, Object> body = new HashMap<>();
        body.put("targetType", type);
        body.put("targetId", target);
        body.put("fields", fields);
        if (links != null) {
            body.put("saleLinks", links);
        }
        return body;
    }

    private static io.restassured.response.ValidatableResponse propose(String user, Map<String, Object> body) {
        return given().auth().oauth2(token(user)).contentType("application/json").body(body)
                .when().post("/api/catalog-edits").then();
    }

    private static String proposeEdition(String user, String edition, Map<String, ?> fields) {
        return propose(user, proposal("EDITION", edition, fields, null)).statusCode(201).extract().path("id");
    }

    private static io.restassured.response.ValidatableResponse decide(String user, String editId, String decision, String note) {
        Map<String, Object> body = new HashMap<>();
        body.put("note", note);
        return given().auth().oauth2(token(user)).contentType("application/json").body(body)
                .when().post("/api/admin/catalog-edits/" + editId + "/" + decision).then();
    }

    private static Map<String, Object> adminEntry(String editId, String status) {
        var request = given().auth().oauth2(token("alice")).queryParam("size", 100);
        if (status != null) {
            request.queryParam("status", status);
        }
        return request.when().get("/api/admin/catalog-edits").then().statusCode(200).extract().jsonPath()
                .getMap("items.find { it.id == '" + editId + "' }");
    }

    private static String storePage(String title, String isbn) {
        return "<html><head><title>" + title + " - Fnac Livre</title></head><body><h1>" + title
                + "</h1><table><tr><td>EAN</td><td>" + isbn.substring(0, 3) + "-" + isbn.substring(3)
                + "</td></tr></table></body></html>";
    }

    // ── Submitting ────────────────────────────────────────────────────────────

    @Test
    void aMemberProposalWaitsForAnAdminAndChangesNothingMeanwhile() {
        String title = "Edit - pending " + UUID.randomUUID();
        String item = addBook("bob", title, randomIsbn());
        String edition = editionOf("bob", item);

        propose("bob", proposal("EDITION", edition, Map.of("title", "Corrected " + title, "publisher", "New Press"), null))
                .statusCode(201)
                .body("id", notNullValue())
                .body("status", is("PENDING"))
                .body("verification", is("NOT_RUN"));

        assertEquals(title, book("bob", item).getString("book.title"));
        assertEquals("Old Press", book("bob", item).getString("book.publisher"));
    }

    @Test
    void theFormIsSeededWithTheCurrentValuesAndSaysWhenAProposalIsPending() {
        String title = "Edit - fields " + UUID.randomUUID();
        String isbn = randomIsbn();
        String edition = editionOf("bob", addBook("bob", title, isbn));

        JsonPath fields = given().auth().oauth2(token("bob"))
                .queryParam("targetType", "EDITION").queryParam("targetId", edition)
                .when().get("/api/catalog-edits/fields").then().statusCode(200).extract().jsonPath();
        assertEquals(title, fields.getString("fields.title"));
        assertEquals(isbn, fields.getString("fields.isbn13"));
        assertEquals("200", fields.getString("fields.pageCount"));
        assertEquals(false, fields.getBoolean("pendingByMe"));

        proposeEdition("bob", edition, Map.of("synopsis", "Un résumé."));

        given().auth().oauth2(token("bob"))
                .queryParam("targetType", "EDITION").queryParam("targetId", edition)
                .when().get("/api/catalog-edits/fields").then().statusCode(200).body("pendingByMe", is(true));
        // Another member's pending proposal is theirs, not carol's.
        given().auth().oauth2(token("carol"))
                .queryParam("targetType", "EDITION").queryParam("targetId", edition)
                .when().get("/api/catalog-edits/fields").then().statusCode(200).body("pendingByMe", is(false));
    }

    @Test
    void oneMemberHasOnePendingProposalPerRecord() {
        String edition = editionOf("bob", addBook("bob", "Edit - twice " + UUID.randomUUID(), randomIsbn()));
        proposeEdition("bob", edition, Map.of("publisher", "First"));

        propose("bob", proposal("EDITION", edition, Map.of("publisher", "Second"), null)).statusCode(409);
        // Someone else may still propose on the same record.
        propose("carol", proposal("EDITION", edition, Map.of("publisher", "Carol's"), null)).statusCode(201);
    }

    @Test
    void aProposalThatIsInvalidOrChangesNothingIsRefused() {
        String title = "Edit - invalid " + UUID.randomUUID();
        String edition = editionOf("bob", addBook("bob", title, randomIsbn()));

        propose("bob", proposal("EDITION", edition, Map.of("nope", "x"), null)).statusCode(400);
        propose("bob", proposal("EDITION", edition, Map.of("pageCount", "abc"), null)).statusCode(400);
        propose("bob", proposal("EDITION", edition, Map.of("pageCount", "0"), null)).statusCode(400);
        propose("bob", proposal("EDITION", edition, Map.of("isbn13", "12345"), null)).statusCode(400);
        propose("bob", proposal("EDITION", edition, Map.of("releaseDate", "31/12/2020"), null)).statusCode(400);
        propose("bob", proposal("EDITION", edition, Map.of("coverUrl", "javascript:alert(1)"), null)).statusCode(400);
        propose("bob", proposal("EDITION", edition, Map.of("title", " "), null)).statusCode(400);
        // The very values the record already holds: there is nothing to review.
        propose("bob", proposal("EDITION", edition, Map.of("title", title, "publisher", "Old Press"), null)).statusCode(400);
        propose("bob", proposal("EDITION", UUID.randomUUID().toString(), Map.of("title", "x"), null)).statusCode(404);
        propose("bob", proposal("SERIES", UUID.randomUUID().toString(), Map.of("title", "x"), null)).statusCode(404);
    }

    @Test
    void onlyTheFieldsThatChangeAreRecorded() {
        String title = "Edit - diff " + UUID.randomUUID();
        String edition = editionOf("bob", addBook("bob", title, randomIsbn()));
        String edit = proposeEdition("bob", edition, Map.of("title", title, "publisher", "New Press"));

        Map<String, Object> entry = adminEntry(edit, "PENDING");
        assertNotNull(entry);
        List<Map<String, Object>> changes = cast(entry.get("changes"));
        assertEquals(1, changes.size());
        assertEquals("publisher", changes.get(0).get("field"));
        assertEquals("Old Press", changes.get(0).get("before"));
        assertEquals("New Press", changes.get(0).get("after"));
        assertEquals(title, entry.get("targetTitle"));
    }

    // ── Reviewing ─────────────────────────────────────────────────────────────

    @Test
    void anApprovedProposalReachesEveryReaderAndNotifiesItsAuthor() {
        String title = "Edit - approved " + UUID.randomUUID();
        String isbn = randomIsbn();
        String bobItem = addBook("bob", title, isbn);
        String edition = editionOf("bob", bobItem);
        String edit = proposeEdition("bob", edition, Map.of("title", "Approved " + title,
                "authors", "Jane Doe, John Roe", "pageCount", "321", "releaseDate", "2021-03-04"));

        decide("alice", edit, "approve", null).statusCode(200).body("status", is("APPLIED"));

        JsonPath bob = book("bob", bobItem);
        assertEquals("Approved " + title, bob.getString("book.title"));
        assertEquals("Jane Doe, John Roe", bob.getString("book.authors"));
        assertEquals(321, bob.getInt("book.pageCount"));
        assertEquals("2021-03-04", bob.getString("book.releaseDate"));

        given().auth().oauth2(token("bob")).when().get("/api/notifications").then().statusCode(200)
                .body("items.find { it.edit != null && it.edit.id == '" + edit + "' }.type", is("EDIT"))
                .body("items.find { it.edit != null && it.edit.id == '" + edit + "' }.edit.status", is("APPLIED"));
        // A decided proposal leaves the pending list.
        assertEquals(null, adminEntry(edit, "PENDING"));
        assertNotNull(adminEntry(edit, "APPLIED"));
    }

    @Test
    void aRejectedProposalChangesNothingAndTellsWhy() {
        String title = "Edit - rejected " + UUID.randomUUID();
        String item = addBook("bob", title, randomIsbn());
        String edit = proposeEdition("bob", editionOf("bob", item), Map.of("title", "Wrong " + title));

        decide("alice", edit, "reject", "Le titre était juste.").statusCode(200).body("status", is("REJECTED"));

        assertEquals(title, book("bob", item).getString("book.title"));
        Map<String, Object> entry = adminEntry(edit, "REJECTED");
        assertEquals("Le titre était juste.", entry.get("reviewNote"));
        given().auth().oauth2(token("bob")).when().get("/api/notifications").then().statusCode(200)
                .body("items.find { it.edit != null && it.edit.id == '" + edit + "' }.edit.note",
                        is("Le titre était juste."));
        // A decided proposal is decided once.
        decide("alice", edit, "approve", null).statusCode(409);
    }

    @Test
    void anAppliedProposalCanBeRevertedToTheValuesItReplaced() {
        String title = "Edit - reverted " + UUID.randomUUID();
        String item = addBook("bob", title, randomIsbn());
        String edit = proposeEdition("bob", editionOf("bob", item), Map.of("title", "Vandal " + title, "publisher", ""));

        decide("alice", edit, "revert", null).statusCode(409);
        decide("alice", edit, "approve", null).statusCode(200);
        assertEquals(null, book("bob", item).getString("book.publisher"));

        decide("alice", edit, "revert", "Vandalisme").statusCode(200).body("status", is("REVERTED"));

        assertEquals(title, book("bob", item).getString("book.title"));
        assertEquals("Old Press", book("bob", item).getString("book.publisher"));
    }

    @Test
    void anAdminProposalIsAppliedAtOnceAndStillRecorded() {
        String title = "Edit - admin " + UUID.randomUUID();
        String item = addBook("alice", title, randomIsbn());

        String edit = propose("alice", proposal("EDITION", editionOf("alice", item), Map.of("title", "Admin " + title), null))
                .statusCode(201).body("status", is("APPLIED")).extract().path("id");

        assertEquals("Admin " + title, book("alice", item).getString("book.title"));
        assertNotNull(adminEntry(edit, "APPLIED"));
    }

    /** #631: a trusted member changes the record at once; an admin can still revert it. */
    @Test
    void aTrustedMembersProposalAppliesAtOnceAndStaysRevertable() {
        String bobId = given().auth().oauth2(token("bob")).when().get("/api/me").then().statusCode(200).extract().path("id");
        String title = "Edit - trusted " + UUID.randomUUID();
        String item = addBook("bob", title, randomIsbn());
        given().auth().oauth2(token("alice")).contentType("application/json").body(Map.of("mode", "TRUSTED"))
                .when().put("/api/admin/users/" + bobId + "/trust").then().statusCode(204);
        try {
            String edit = propose("bob", proposal("EDITION", editionOf("bob", item), Map.of("title", "Trusted " + title), null))
                    .statusCode(201).body("status", is("APPLIED")).extract().path("id");
            assertEquals("Trusted " + title, book("bob", item).getString("book.title"));
            Map<String, Object> entry = adminEntry(edit, "APPLIED");
            assertEquals(true, entry.get("appliedByTrust"));

            decide("alice", edit, "revert", null).statusCode(200);
            assertEquals(title, book("bob", item).getString("book.title"));
        } finally {
            given().auth().oauth2(token("alice")).contentType("application/json").body(Map.of("mode", "AUTO"))
                    .when().put("/api/admin/users/" + bobId + "/trust").then().statusCode(204);
        }
        // Back to automatic, bob (a fresh account) is not trusted: his next proposal waits.
        propose("bob", proposal("EDITION", editionOf("bob", item), Map.of("publisher", "Waits"), null))
                .statusCode(201).body("status", is("PENDING"));
    }

    @Test
    void onlyAnAdminReviews() {
        String edit = proposeEdition("bob", editionOf("bob", addBook("bob", "Edit - 403 " + UUID.randomUUID(), randomIsbn())),
                Map.of("publisher", "Mine"));

        given().auth().oauth2(token("bob")).when().get("/api/admin/catalog-edits").then().statusCode(403);
        decide("bob", edit, "approve", null).statusCode(403);
        decide("carol", edit, "reject", null).statusCode(403);
        decide("alice", UUID.randomUUID().toString(), "approve", null).statusCode(404);
        given().auth().oauth2(token("alice")).queryParam("status", "NOPE")
                .when().get("/api/admin/catalog-edits").then().statusCode(400);
    }

    @Test
    void aSeriesProposalIsReviewedTheSameWay() {
        String title = "Edit - series " + UUID.randomUUID();
        given().auth().oauth2(token("bob")).contentType("application/json")
                .body(Map.of("book", Map.of("kind", "MANGA", "title", title + " 1", "seriesTitle", title, "volumeNumber", 1),
                        "status", "TO_READ"))
                .when().post("/api/library").then().statusCode(201);
        String series = given().auth().oauth2(token("bob")).when().get("/api/series").then().statusCode(200)
                .extract().path("find { it.title == '" + title + "' }.id");

        propose("bob", proposal("SERIES", series, Map.of("status", "WHATEVER"), null)).statusCode(400);
        String edit = propose("bob", proposal("SERIES", series,
                Map.of("totalVolumes", "12", "status", "COMPLETED", "originalTitle", "オリジナル"), null))
                .statusCode(201).extract().path("id");
        decide("alice", edit, "approve", null).statusCode(200);

        given().auth().oauth2(token("bob")).when().get("/api/series/" + series).then().statusCode(200)
                .body("totalVolumes", is(12))
                .body("status", is("COMPLETED"))
                .body("originalTitle", is("オリジナル"));
    }

    // ── Sale links and the automatic check ───────────────────────────────────

    @Test
    void aSaleLinkMustPointAtAKnownStoreOverHttps() {
        String edition = editionOf("bob", addBook("bob", "Edit - links " + UUID.randomUUID(), randomIsbn()));

        for (String url : List.of("https://example.com/book", "http://www.fnac.com/a123",
                "https://www.fnac.com.evil.test/a123", "https://evilamazon.fr/dp/1", "not a url",
                "https://user@www.fnac.com/a1")) {
            propose("bob", proposal("EDITION", edition, Map.of(), List.of(url))).statusCode(400);
        }
        // One link per store.
        propose("bob", proposal("EDITION", edition, Map.of(),
                List.of("https://www.fnac.com/a1", "https://livre.fnac.com/a2"))).statusCode(400);
    }

    /** #628: a link with ad-tracking parameters is accepted, and stored without them. */
    @Test
    void aStoreLinkIsStoredWithoutItsTrackingParameters() {
        String edition = editionOf("alice", addBook("alice", "Edit - tracking " + UUID.randomUUID(), randomIsbn()));
        String tracked = "https://www.fnac.com/a22917644/Becky-Chambers?oref=0&esl-k=sem-google|nx|c|m&gclid=Cj0K";

        propose("alice", proposal("EDITION", edition, Map.of(), List.of(tracked))).statusCode(201);

        given().auth().oauth2(token("bob")).when().get("/api/editions/" + edition + "/sale-links")
                .then().statusCode(200).body("url", hasItem("https://www.fnac.com/a22917644/Becky-Chambers"));
    }

    @Test
    void aStorePageThatConfirmsTheBookAppliesTheProposalAtOnce() {
        String title = "Edit - verified " + UUID.randomUUID();
        String isbn = randomIsbn();
        String item = addBook("bob", title, isbn);
        String edition = editionOf("bob", item);
        String url = "https://www.fnac.com/a" + isbn;
        stores.serve(url, storePage(title.toUpperCase(), isbn));

        String edit = propose("bob", proposal("EDITION", edition, Map.of("publisher", "Verified Press"), List.of(url)))
                .statusCode(201)
                .body("status", is("APPLIED"))
                .body("verification", is("PASSED"))
                .extract().path("id");

        assertEquals("Verified Press", book("bob", item).getString("book.publisher"));
        given().auth().oauth2(token("carol")).when().get("/api/editions/" + edition + "/sale-links")
                .then().statusCode(200)
                .body("store", hasItem("FNAC"))
                .body("url", hasItem(url));
        Map<String, Object> entry = adminEntry(edit, "APPLIED");
        assertEquals("PASSED", entry.get("verification"));
        assertEquals(url, entry.get("verificationUrl"));
    }

    @Test
    void aStorePageThatDoesNotConfirmTheBookLeavesTheProposalToAnAdmin() {
        String title = "Edit - unverified " + UUID.randomUUID();
        String isbn = randomIsbn();
        String edition = editionOf("bob", addBook("bob", title, isbn));
        String url = "https://www.decitre.fr/livres/" + isbn;
        stores.serve(url, storePage(title, randomIsbn()));

        String edit = propose("bob", proposal("EDITION", edition, Map.of(), List.of(url)))
                .statusCode(201)
                .body("status", is("PENDING"))
                .body("verification", is("FAILED"))
                .body("verificationDetail", is("ISBN_NOT_FOUND"))
                .extract().path("id");

        given().auth().oauth2(token("bob")).when().get("/api/editions/" + edition + "/sale-links")
                .then().statusCode(200).body("url", not(hasItem(url)));
        assertEquals("FAILED", adminEntry(edit, "PENDING").get("verification"));

        decide("alice", edit, "approve", null).statusCode(200);
        given().auth().oauth2(token("bob")).when().get("/api/editions/" + edition + "/sale-links")
                .then().statusCode(200).body("url", hasItem(url));
    }

    @Test
    void aStoreThatCannotBeReachedLeavesTheProposalToAnAdmin() {
        String edition = editionOf("bob", addBook("bob", "Edit - blocked " + UUID.randomUUID(), randomIsbn()));

        propose("bob", proposal("EDITION", edition, Map.of(), List.of("https://www.amazon.fr/dp/2070612880")))
                .statusCode(201)
                .body("status", is("PENDING"))
                .body("verification", is("FAILED"))
                .body("verificationDetail", is("FETCH_FAILED"));
    }

    @Test
    void aStorePageWithTheWrongTitleIsNotAConfirmation() {
        String isbn = randomIsbn();
        String edition = editionOf("bob", addBook("bob", "Edit - title " + UUID.randomUUID(), isbn));
        String url = "https://www.cultura.com/p-" + isbn + ".html";
        stores.serve(url, storePage("Un tout autre livre", isbn));

        propose("bob", proposal("EDITION", edition, Map.of(), List.of(url)))
                .statusCode(201)
                .body("status", is("PENDING"))
                .body("verificationDetail", is("TITLE_NOT_FOUND"));
    }

    @Test
    void removingASaleLinkGoesThroughReview() {
        String title = "Edit - unlink " + UUID.randomUUID();
        String isbn = randomIsbn();
        String edition = editionOf("alice", addBook("alice", title, isbn));
        String url = "https://www.gibert.com/" + isbn;
        propose("alice", proposal("EDITION", edition, Map.of(), List.of(url))).statusCode(201);

        String edit = propose("bob", proposal("EDITION", edition, Map.of(), List.of()))
                .statusCode(201).body("status", is("PENDING")).body("verification", is("NOT_RUN"))
                .extract().path("id");
        List<Map<String, Object>> changes = cast(adminEntry(edit, "PENDING").get("changes"));
        assertEquals("saleLinks", changes.get(0).get("field"));
        assertEquals(url, changes.get(0).get("before"));

        decide("alice", edit, "approve", null).statusCode(200);
        given().auth().oauth2(token("bob")).when().get("/api/editions/" + edition + "/sale-links")
                .then().statusCode(200).body("size()", is(0));
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }
}
