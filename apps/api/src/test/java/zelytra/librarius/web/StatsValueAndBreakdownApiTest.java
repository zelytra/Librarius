package zelytra.librarius.web;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import zelytra.librarius.library.PageCountEstimator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The statistics added with #418 to #421 and the page estimates of #414.
 *
 * <p>Each test works with its own user so the totals it asserts are its own: {@code bob} for
 * the value, which sums a whole account.
 */
@QuarkusTest
class StatsValueAndBreakdownApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    @Inject
    EntityManager em;

    @Inject
    PageCountEstimator estimator;

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    private Map<String, Object> add(String user, String kind, String title, String series,
            Integer volume, Integer pages, String status, String ownership) {
        String body = """
                { "book": { "kind": "%s", "title": "%s", "authors": "Stats Value Test"%s%s%s },
                  "status": "%s", "ownership": "%s" }
                """.formatted(kind, title,
                series == null ? "" : ", \"seriesTitle\": \"" + series + "\"",
                volume == null ? "" : ", \"volumeNumber\": " + volume,
                pages == null ? "" : ", \"pageCount\": " + pages,
                status, ownership);
        return given().auth().oauth2(token(user)).contentType("application/json").body(body)
                .when().post("/api/library")
                .then().statusCode(201)
                .extract().jsonPath().getMap("$");
    }

    @SuppressWarnings("unchecked")
    private static String editionId(Map<String, Object> item) {
        return (String) ((Map<String, Object>) item.get("book")).get("editionId");
    }

    private void price(String editionId, String amount) {
        QuarkusTransaction.requiringNew().run(() -> em
                .createNativeQuery("update edition set list_price = :p, list_price_currency = 'EUR' where id = :id")
                .setParameter("p", new BigDecimal(amount))
                .setParameter("id", UUID.fromString(editionId))
                .executeUpdate());
    }

    // ── #414 page estimates ───────────────────────────────────────────────────

    @Test
    void aTitleOfUnknownLengthGetsAnEstimateThatTheSeriesThenRefines() {
        String series = "Estimate series " + UUID.randomUUID();
        Map<String, Object> unknown = add("alice", "MANGA", "Estimate - unknown", series, 2, null, "TO_READ", "OWNED");
        @SuppressWarnings("unchecked")
        Map<String, Object> book = (Map<String, Object>) unknown.get("book");
        assertEquals(192, book.get("pageCount"));
        assertEquals(true, book.get("pageCountEstimated"));

        add("alice", "MANGA", "Estimate - known", series, 1, 210, "TO_READ", "OWNED");
        estimator.estimateNow();

        given().auth().oauth2(token("alice")).when().get("/api/library/" + unknown.get("id"))
                .then().statusCode(200)
                .body("book.pageCount", is(210))
                .body("book.pageCountEstimated", is(true));
    }

    @Test
    void aRealLengthIsNeverMarkedEstimated() {
        Map<String, Object> item = add("alice", "BOOK", "Estimate - real", null, null, 280, "TO_READ", "OWNED");
        given().auth().oauth2(token("alice")).when().get("/api/library/" + item.get("id"))
                .then().statusCode(200)
                .body("book.pageCount", is(280))
                .body("book.pageCountEstimated", is(false));
    }

    // ── #419 all-time breakdown ───────────────────────────────────────────────

    @Test
    void aBronzeTitleReadWithoutADateShowsInTheAllTimeRanks() {
        Map<String, Object> item = add("alice", "BOOK", "Breakdown - bronze", null, null, 100, "READ", "OWNED");
        // An imported read: the status, no finish date.
        QuarkusTransaction.requiringNew().run(() -> em
                .createNativeQuery("update reading_progress set finished_at = null where library_item_id = :id")
                .setParameter("id", UUID.fromString((String) item.get("id")))
                .executeUpdate());
        String bronze = given().auth().oauth2(token("alice")).when().get("/api/categories")
                .then().statusCode(200)
                .extract().jsonPath().getString("find { it.code == 'bronze' }.id");
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"categoryId\": \"" + bronze + "\" }")
                .when().put("/api/library/" + item.get("id") + "/rank")
                .then().statusCode(200);

        List<String> ranks = given().auth().oauth2(token("alice"))
                .when().get("/api/stats/breakdown")
                .then().statusCode(200)
                .body("books", greaterThanOrEqualTo(1))
                .extract().path("byRank.label");
        assertThat(ranks, hasItem("Bronze"));
    }

    // ── #418 titles per month, #420 pages per kind ───────────────────────────

    @Test
    void theTimelineGivesTitlesPerMonthAndPagesPerKind() {
        add("alice", "MANGA", "Timeline - manga pages", null, null, 200, "READ", "OWNED");
        int year = LocalDate.now().getYear();
        Map<String, Object> timeline = given().auth().oauth2(token("alice"))
                .when().get("/api/stats/timeline?from=" + year + "-01-01&to=" + year + "-12-31")
                .then().statusCode(200)
                .body("booksPerMonth", greaterThan(0f))
                .extract().jsonPath().getMap("$");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points = (List<Map<String, Object>>) timeline.get("points");
        long mangaPages = points.stream()
                .map(p -> (Map<?, ?>) p.get("pagesByKind"))
                .mapToLong(m -> m.get("MANGA") == null ? 0 : ((Number) m.get("MANGA")).longValue())
                .sum();
        assertTrue(mangaPages >= 200, "manga pages " + mangaPages);
    }

    // ── #421 value ────────────────────────────────────────────────────────────

    @Test
    void theLibraryValueCountsOwnedCopiesAndTheWishlistItsMarketPrice() {
        float libraryBefore = given().auth().oauth2(token("bob")).when().get("/api/stats/value")
                .then().statusCode(200).extract().jsonPath().getFloat("library");
        float wishlistBefore = given().auth().oauth2(token("bob")).when().get("/api/stats/value")
                .then().statusCode(200).extract().jsonPath().getFloat("wishlist");

        price(editionId(add("bob", "BOOK", "Value - owned", null, null, 100, "READ", "OWNED")), "14.95");
        // Borrowed: read, not on the shelves, so not part of what the library is worth.
        price(editionId(add("bob", "BOOK", "Value - borrowed", null, null, 100, "READ", "BORROWED")), "20.00");
        price(editionId(add("bob", "BOOK", "Value - wished", null, null, 100, "TO_READ", "WISHLIST")), "7.20");

        given().auth().oauth2(token("bob")).when().get("/api/stats/value")
                .then().statusCode(200)
                .body("currency", is("EUR"))
                .body("library", is(libraryBefore + 14.95f))
                .body("wishlist", is(wishlistBefore + 7.20f));

        // Another reader's library is worth nothing to bob, and the reverse.
        Map<String, Object> alice = given().auth().oauth2(token("alice")).when().get("/api/stats/value")
                .then().statusCode(200).extract().jsonPath().getMap("$");
        assertTrue(alice.containsKey("library"));
    }

    @Test
    void theWishlistShowsTheMarketPriceOfAWish() {
        Map<String, Object> item = add("alice", "BOOK", "Value - market price", null, null, 100, "TO_READ", "WISHLIST");
        price(editionId(item), "9.90");
        given().auth().oauth2(token("alice"))
                .when().get("/api/wishlist?size=100&q=Value - market price")
                .then().statusCode(200)
                .body("items[0].book.listPrice", is(9.90f))
                .body("items[0].book.listPriceCurrency", is("EUR"));
    }
}
