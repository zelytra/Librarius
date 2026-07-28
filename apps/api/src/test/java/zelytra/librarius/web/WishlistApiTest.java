package zelytra.librarius.web;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.LibraryStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ordering, editing, budget and conversion of the wishlist.
 *
 * <p>The test database is shared by the whole suite, so every request carries a {@code q}
 * matching an author name invented for the case at hand. Totals can then be asserted
 * exactly, and adding a test never shifts the figures of another.
 */
@QuarkusTest
class WishlistApiTest {

    private static final KeycloakTestClient KEYCLOAK = new KeycloakTestClient();

    /** Tolerance on the money assertions: JSON numbers come back as doubles. */
    private static final double CENT = 0.001;

    @Inject
    EntityManager em;

    private static String token(String user) {
        return KEYCLOAK.getAccessToken(user);
    }

    /**
     * Adds a wish and returns the creation response, from which a test reads the identifier
     * and, when it needs it, the edition the wish points at.
     *
     * @param price raw JSON — a number, or {@code null} for a wish carrying no estimate
     */
    private static ValidatableResponse addWish(String user, String title, String priority,
            String price, String marker) {
        return given().auth().oauth2(token(user)).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "%s", "authors": "%s" },
                          "priority": "%s", "estimatedPrice": %s }
                        """.formatted(title, marker, priority, price))
                .when().post("/api/wishlist")
                .then().statusCode(201);
    }

    /** Reads the wishlist restricted to one marker, with extra query parameters. */
    private static ValidatableResponse list(String user, String marker, String query) {
        RequestSpecification request = given().auth().oauth2(token(user)).queryParam("q", marker);
        if (!query.isEmpty()) {
            for (String pair : query.split("&")) {
                String[] keyValue = pair.split("=", 2);
                request = request.queryParam(keyValue[0], keyValue[1]);
            }
        }
        return request.when().get("/api/wishlist").then().statusCode(200);
    }

    // ── Ordering by urgency (#114) ────────────────────────────────────────────

    /**
     * The priority is stored as its name, so ordering on the column sorted
     * {@code PRIORITY, SOMEDAY, SOON} and showed the wishes with no date attached ahead of
     * the ones the user meant to buy next.
     *
     * <p>All three priorities are present on purpose: with only two of them, the broken
     * ordering and the right one agree half the time and the test proves nothing.
     */
    @Test
    void theWishlistRunsFromTheMostUrgentToTheLeast() {
        String marker = "Urgencia Fixtura";
        // Seeded in yet another order, so that neither the insertion order nor the
        // alphabetical one can pass for the urgency order.
        addWish("alice", "Someday urgencia", "SOMEDAY", "null", marker);
        addWish("alice", "Priority urgencia", "PRIORITY", "null", marker);
        addWish("alice", "Soon urgencia", "SOON", "null", marker);

        list("alice", marker, "")
                .body("total", is(3))
                .body("items.priority", contains("PRIORITY", "SOON", "SOMEDAY"));

        // Same order when the client asks for it explicitly rather than taking the default.
        list("alice", marker, "sort=priority")
                .body("items.priority", contains("PRIORITY", "SOON", "SOMEDAY"));
    }

    /**
     * Paging through the wishlist two at a time must return the same sequence as the single
     * request — including for the two wishes sharing a priority, which the tie-break on the
     * identifier keeps on their own side of the page boundary.
     */
    @Test
    void theUrgencyOrderSurvivesPagination() {
        String marker = "Paginata Urgencia";
        addWish("alice", "Someday paginata", "SOMEDAY", "null", marker);
        addWish("alice", "Soon paginata one", "SOON", "null", marker);
        addWish("alice", "Priority paginata", "PRIORITY", "null", marker);
        addWish("alice", "Soon paginata two", "SOON", "null", marker);

        List<String> whole = list("alice", marker, "size=200")
                .body("items.priority", contains("PRIORITY", "SOON", "SOON", "SOMEDAY"))
                .extract().jsonPath().getList("items.id", String.class);

        List<String> paged = new ArrayList<>();
        for (int page = 0; page < 2; page++) {
            paged.addAll(list("alice", marker, "page=" + page + "&size=2")
                    .extract().jsonPath().getList("items.id", String.class));
        }
        assertEquals(whole, paged, "the pages must follow the same order as the whole list");
    }

    // ── Editing (#52) ─────────────────────────────────────────────────────────

    @Test
    void aWishCanBeReprioritisedRepricedAndAnnotated() {
        String marker = "Editia Fixtura";
        String id = addWish("alice", "Editable editia", "SOMEDAY", "9.99", marker)
                .extract().path("id");

        ValidatableResponse updated = given().auth().oauth2(token("alice"))
                .contentType("application/json")
                .body("""
                        { "priority": "PRIORITY", "estimatedPrice": 24.50,
                          "note": "Collector edition" }
                        """)
                .when().put("/api/wishlist/" + id)
                .then().statusCode(200)
                .body("id", is(id))
                .body("priority", is("PRIORITY"))
                .body("note", is("Collector edition"));
        assertEquals(24.50, updated.extract().jsonPath().getDouble("estimatedPrice"), CENT);

        // Persisted, and the wish has moved to the head of the list with its new priority.
        list("alice", marker, "")
                .body("items[0].id", is(id))
                .body("items[0].priority", is("PRIORITY"))
                .body("items[0].note", is("Collector edition"));
    }

    /** A PUT replaces the three fields: leaving one out clears it rather than keeping it. */
    @Test
    void anAbsentPriceOrNoteClearsTheField() {
        String marker = "Clearia Fixtura";
        String id = addWish("alice", "Cleared clearia", "SOON", "12.00", marker)
                .extract().path("id");
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"priority\": \"SOON\", \"note\": \"To be dropped\" }")
                .when().put("/api/wishlist/" + id).then().statusCode(200);

        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"priority\": \"SOMEDAY\" }")
                .when().put("/api/wishlist/" + id)
                .then().statusCode(200)
                .body("priority", is("SOMEDAY"))
                .body("estimatedPrice", nullValue())
                .body("note", nullValue());
    }

    /**
     * The bounds are those of the columns. Without them an oversized price reaches
     * PostgreSQL and surfaces as a 500, which reads to the client as a server bug.
     */
    @Test
    void anInvalidUpdateIsRejectedWithoutTouchingTheWish() {
        String marker = "Invalida Fixtura";
        String id = addWish("alice", "Guarded invalida", "SOON", "12.00", marker)
                .extract().path("id");

        for (String body : new String[] {
                "{ \"estimatedPrice\": 10.00 }",                             // no priority
                "{ \"priority\": \"SOON\", \"estimatedPrice\": -1.00 }",     // negative
                "{ \"priority\": \"SOON\", \"estimatedPrice\": 12345678.00 }" }) {
            given().auth().oauth2(token("alice")).contentType("application/json").body(body)
                    .when().put("/api/wishlist/" + id)
                    .then().statusCode(400);
        }

        list("alice", marker, "").body("items[0].priority", is("SOON"));
    }

    @Test
    void updatingAnUnknownWishAnswersNotFound() {
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"priority\": \"SOON\" }")
                .when().put("/api/wishlist/" + UUID.randomUUID())
                .then().statusCode(404);
    }

    // ── Conversion (#52) ──────────────────────────────────────────────────────

    /**
     * "I bought it" in one gesture: the owned title appears, the wish disappears, and the
     * collection points at the very edition the wish described — no second catalog row for
     * a book the user had already entered.
     */
    @Test
    void buyingAWishMovesItIntoTheCollectionWithoutDuplicatingTheEdition() {
        String marker = "Acquira Fixtura";
        ValidatableResponse wish = addWish("alice", "Bought acquira", "PRIORITY", "18.00", marker);
        String wishId = wish.extract().path("id");
        String editionId = wish.extract().path("book.editionId");

        String itemId = given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"status\": \"READING\", \"acquiredAt\": \"2026-07-28\" }")
                .when().post("/api/wishlist/" + wishId + "/acquire")
                .then().statusCode(201)
                .body("status", is("READING"))
                .body("acquiredAt", is("2026-07-28"))
                .body("book.title", is("Bought acquira"))
                .body("book.editionId", is(editionId))
                .extract().path("id");

        list("alice", marker, "").body("total", is(0));

        given().auth().oauth2(token("alice")).queryParam("q", marker)
                .when().get("/api/library")
                .then().statusCode(200)
                .body("total", is(1))
                .body("items[0].id", is(itemId));
    }

    /** With no purchase details, the title lands in the collection as simply owned. */
    @Test
    void acquiringWithoutDetailsDefaultsToAnOwnedTitle() {
        String marker = "Defaulta Acquira";
        String wishId = addWish("alice", "Bare defaulta", "SOON", "null", marker)
                .extract().path("id");

        given().auth().oauth2(token("alice")).contentType("application/json").body("{}")
                .when().post("/api/wishlist/" + wishId + "/acquire")
                .then().statusCode(201)
                .body("status", is("OWNED"))
                .body("acquiredAt", nullValue());
    }

    @Test
    void acquiringAnUnknownWishAnswersNotFound() {
        given().auth().oauth2(token("alice")).contentType("application/json").body("{}")
                .when().post("/api/wishlist/" + UUID.randomUUID() + "/acquire")
                .then().statusCode(404);
    }

    /**
     * The transaction, verified rather than assumed: a failure on the collection side must
     * not cost the user the wish that recorded the book.
     *
     * <p>The failure is a real one, not a stub. {@code library_item} carries a unique
     * {@code (user_id, edition_id)}: planting an owned title on the very edition the wish
     * points at makes the insert of the acquisition violate it. The wish is deleted first
     * and the insert fails afterwards, so only the rollback can bring it back — an
     * implementation writing outside a transaction would leave the user with neither the
     * wish nor the book.
     */
    @Test
    void aFailedAcquisitionLeavesTheWishIntact() {
        String marker = "Rollbacka Fixtura";
        ValidatableResponse wish = addWish("alice", "Doomed rollbacka", "SOON", "12.00", marker);
        String wishId = wish.extract().path("id");
        UUID editionId = UUID.fromString(wish.extract().path("book.editionId"));
        String aliceId = given().auth().oauth2(token("alice"))
                .when().get("/api/me")
                .then().statusCode(200)
                .extract().path("id");

        QuarkusTransaction.requiringNew().run(() -> {
            LibraryItem squatter = new LibraryItem();
            squatter.userId = aliceId;
            squatter.edition = em.find(Edition.class, editionId);
            squatter.status = LibraryStatus.OWNED;
            em.persist(squatter);
        });

        given().auth().oauth2(token("alice")).contentType("application/json").body("{}")
                .when().post("/api/wishlist/" + wishId + "/acquire")
                .then().statusCode(500);

        list("alice", marker, "")
                .body("total", is(1))
                .body("items[0].id", is(wishId))
                .body("items[0].priority", is("SOON"));
    }

    // ── Budget (#52) ──────────────────────────────────────────────────────────

    /**
     * The budget covers the whole filtered wishlist and not the page in hand, breaks down
     * per priority in the same urgency order as the list, and is recomputed on every read
     * rather than cached alongside it.
     */
    @Test
    void theBudgetTotalsTheWholeFilteredWishlist() {
        String marker = "Budgeta Fixtura";
        addWish("alice", "Priority budgeta", "PRIORITY", "20.00", marker);
        addWish("alice", "Soon budgeta one", "SOON", "10.50", marker);
        String repriced = addWish("alice", "Soon budgeta two", "SOON", "4.50", marker)
                .extract().path("id");
        addWish("alice", "Someday budgeta", "SOMEDAY", "null", marker);

        // One item per page, and still the budget of the four.
        ValidatableResponse page = list("alice", marker, "page=0&size=1");
        page.body("items", hasSize(1))
                .body("total", is(4))
                .body("budget.pricedCount", is(3))
                .body("budget.byPriority.priority", contains("PRIORITY", "SOON", "SOMEDAY"))
                .body("budget.byPriority.count", contains(1, 2, 1))
                .body("budget.byPriority.pricedCount", contains(1, 2, 0));
        assertEquals(35.00, page.extract().jsonPath().getDouble("budget.total"), CENT);
        assertEquals(20.00, page.extract().jsonPath().getDouble("budget.byPriority[0].total"),
                CENT);
        assertEquals(15.00, page.extract().jsonPath().getDouble("budget.byPriority[1].total"),
                CENT);
        // A group where nobody entered a price is zero, not the absence of a figure.
        assertEquals(0.00, page.extract().jsonPath().getDouble("budget.byPriority[2].total"), CENT);

        // A filter narrows the budget exactly as it narrows the list.
        ValidatableResponse soon = list("alice", marker, "priority=SOON");
        assertEquals(15.00, soon.extract().jsonPath().getDouble("budget.total"), CENT);

        // Repricing a wish moves the total: nothing is memoised behind the list.
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"priority\": \"SOON\", \"estimatedPrice\": 9.50 }")
                .when().put("/api/wishlist/" + repriced).then().statusCode(200);
        assertEquals(40.00, list("alice", marker, "").extract().jsonPath()
                .getDouble("budget.total"), CENT);
    }

    /** An empty wishlist reports a zero budget rather than a missing one. */
    @Test
    void anEmptyWishlistHasAZeroBudget() {
        ValidatableResponse empty = list("alice", "Vacua Fixtura", "");
        empty.body("total", is(0))
                .body("budget.pricedCount", is(0))
                .body("budget.byPriority", hasSize(0));
        assertEquals(0.00, empty.extract().jsonPath().getDouble("budget.total"), CENT);
    }
}
