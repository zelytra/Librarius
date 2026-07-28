package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Server-side pagination, sorting and filtering of {@code /api/library} and
 * {@code /api/wishlist}.
 *
 * <p>The test database is shared by the whole suite, so every request below carries a
 * {@code q} matching an author name invented for this class. Totals can then be asserted
 * exactly rather than "at least", which is what actually locks pagination down: an
 * off-by-one in the offset shows up as a duplicated or a missing row.
 *
 * <p>The read-only fixture is seeded once in {@link #seed()} under {@link #MARKER}. Tests
 * that need to create something use a marker of their own, so that adding a case never
 * shifts the totals of the others.
 */
@QuarkusTest
class PaginationApiTest {

    private static final KeycloakTestClient KEYCLOAK = new KeycloakTestClient();

    /** Author of the read-only fixture, and the search term that isolates it. */
    private static final String MARKER = "Paginationa Fixtura";

    /**
     * Seeding order of Alice's books, deliberately not alphabetical: the default ordering
     * (newest first) and the {@code title} ordering must not be able to pass for each
     * other.
     */
    private static final List<String> BOOK_TITLES =
            List.of("Alpha pagination", "Delta pagination", "Bravo pagination");
    private static final List<String> MANGA_TITLES =
            List.of("Zulu one pagination", "Echo one pagination");

    private static boolean seeded;

    private static String token(String user) {
        return KEYCLOAK.getAccessToken(user);
    }

    /**
     * Seeds once for the whole class. Not a {@code @BeforeAll}: Quarkus only points
     * RestAssured at the test port from {@code @BeforeEach} onwards, so anything sent
     * earlier lands on a closed port.
     */
    @BeforeEach
    void seed() {
        if (seeded) {
            return;
        }
        seeded = true;
        BOOK_TITLES.forEach(title -> addLibraryItem("alice", "BOOK", title, "OWNED", MARKER));
        MANGA_TITLES.forEach(title -> addLibraryItem("alice", "MANGA", title, "READ", MARKER));
        BOOK_TITLES.forEach(title -> addWishlistItem("alice", "BOOK", title, "SOON", MARKER));
        MANGA_TITLES.forEach(title -> addWishlistItem("alice", "MANGA", title, "PRIORITY", MARKER));
    }

    private static String addLibraryItem(String user, String kind, String title, String status,
            String marker) {
        return given().auth().oauth2(token(user)).contentType("application/json")
                .body("""
                        { "book": { "kind": "%s", "title": "%s", "authors": "%s" },
                          "status": "%s" }
                        """.formatted(kind, title, marker, status))
                .when().post("/api/library")
                .then().statusCode(201)
                .extract().path("id");
    }

    private static void addWishlistItem(String user, String kind, String title, String priority,
            String marker) {
        given().auth().oauth2(token(user)).contentType("application/json")
                .body("""
                        { "book": { "kind": "%s", "title": "%s", "authors": "%s" },
                          "priority": "%s" }
                        """.formatted(kind, title, marker, priority))
                .when().post("/api/wishlist")
                .then().statusCode(201);
    }

    /**
     * GET restricted to one marker, as the given user.
     *
     * <p>Parameters go through {@code queryParam} rather than a hand-built URL: RestAssured
     * encodes what it is given, so a {@code %20} written by hand would reach the server as
     * a literal {@code %20}.
     */
    private static ValidatableResponse get(String user, String path, String marker, String query) {
        RequestSpecification request = given().auth().oauth2(token(user)).queryParam("q", marker);
        if (!query.isEmpty()) {
            for (String pair : query.split("&")) {
                String[] keyValue = pair.split("=", 2);
                request = request.queryParam(keyValue[0], keyValue[1]);
            }
        }
        return request.when().get(path).then().statusCode(200);
    }

    /** GET restricted to the read-only fixture. */
    private static ValidatableResponse get(String user, String path, String query) {
        return get(user, path, MARKER, query);
    }

    // ── Envelope and pagination ───────────────────────────────────────────────

    @Test
    void firstPageIsCappedBySizeAndCarriesTheFullTotal() {
        get("alice", "/api/library", "page=0&size=2")
                .body("items", hasSize(2))
                .body("page", is(0))
                .body("size", is(2))
                .body("total", is(5));
    }

    /** Three pages of two must cover the five items exactly once. */
    @Test
    void pagesFollowOnWithoutOverlapNorGap() {
        List<String> paged = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            paged.addAll(get("alice", "/api/library", "page=" + page + "&size=2")
                    .extract().jsonPath().getList("items.id", String.class));
        }
        assertEquals(5, paged.size(), "the three pages must return the five items");
        assertEquals(5, Set.copyOf(paged).size(), "an item must not appear on two pages");
    }

    @Test
    void aPageBeyondTheEndIsEmptyButStillReportsTheTotal() {
        get("alice", "/api/library", "page=50&size=2")
                .body("items", hasSize(0))
                .body("total", is(5));
    }

    /**
     * A client asking for more than the ceiling gets the ceiling rather than a 400, and
     * the envelope echoes the size actually applied.
     */
    @Test
    void sizeIsClampedAndOutOfRangeValuesAreBroughtBackIn() {
        get("alice", "/api/library", "size=5000").body("size", is(200));
        get("alice", "/api/library", "page=-3&size=-1").body("page", is(0)).body("size", is(1));
    }

    @Test
    void theDefaultPageIsTheFirstFifty() {
        get("alice", "/api/library", "").body("size", is(50)).body("page", is(0));
    }

    // ── Filters ───────────────────────────────────────────────────────────────

    /** The combination spelled out in the acceptance criteria of the issue. */
    @Test
    void theDocumentedCombinationOfParametersWorks() {
        given().auth().oauth2(token("alice"))
                .when().get("/api/library?page=0&size=50&sort=title&kind=MANGA&q=one")
                .then().statusCode(200)
                .body("page", is(0))
                .body("size", is(50))
                .body("items.book.kind", everyItem(is("MANGA")))
                .body("items.book.title", hasItem("Echo one pagination"))
                .body("items.book.title", hasItem("Zulu one pagination"));
    }

    @Test
    void kindNarrowsTheShelf() {
        get("alice", "/api/library", "kind=MANGA")
                .body("total", is(2))
                .body("items.book.kind", everyItem(is("MANGA")));
        get("alice", "/api/library", "kind=BOOK")
                .body("total", is(3))
                .body("items.book.kind", everyItem(is("BOOK")));
    }

    @Test
    void statusNarrowsTheShelf() {
        get("alice", "/api/library", "status=READ")
                .body("total", is(2))
                .body("items.status", everyItem(is("READ")));
        get("alice", "/api/library", "status=READING").body("total", is(0));
    }

    @Test
    void kindAndStatusCombine() {
        get("alice", "/api/library", "kind=MANGA&status=READ").body("total", is(2));
        get("alice", "/api/library", "kind=BOOK&status=READ").body("total", is(0));
    }

    /** The free text hits the author (the marker) as well as the title. */
    @Test
    void searchMatchesTheAuthorAndTheTitleAlike() {
        get("alice", "/api/library", "").body("total", is(5));
        get("alice", "/api/library", "kind=MANGA&sort=title")
                .body("items.book.title", contains("Echo one pagination", "Zulu one pagination"));
    }

    @Test
    void searchIsCaseInsensitiveAndTrimmed() {
        given().auth().oauth2(token("alice")).queryParam("q", "  ALPHA PAGINATION  ")
                .when().get("/api/library")
                .then().statusCode(200)
                .body("items.book.title", hasItem("Alpha pagination"));
    }

    /** A wildcard typed by the user is searched literally, not as a match-everything. */
    @Test
    void searchWildcardsAreEscaped() {
        given().auth().oauth2(token("alice")).queryParam("q", "%")
                .when().get("/api/library")
                .then().statusCode(200)
                .body("total", is(0));
    }

    @Test
    void rankNarrowsTheShelf() {
        String marker = "Rankera Fixtura";
        String ranked = addLibraryItem("alice", "BOOK", "Ranked pagination", "OWNED", marker);
        addLibraryItem("alice", "BOOK", "Unranked pagination", "OWNED", marker);

        String orId = given().auth().oauth2(token("alice")).when().get("/api/categories")
                .then().statusCode(200)
                .extract().path("find { it.code == 'or' }.id");
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"categoryId\": \"" + orId + "\" }")
                .when().put("/api/library/" + ranked + "/rank")
                .then().statusCode(200);

        get("alice", "/api/library", marker, "rank=or")
                .body("total", is(1))
                .body("items[0].id", is(ranked));
        get("alice", "/api/library", marker, "rank=bronze").body("total", is(0));
        get("alice", "/api/library", marker, "").body("total", is(2));
    }

    // ── Sorting ───────────────────────────────────────────────────────────────

    @Test
    void sortByTitleIsAlphabeticalAndTheDefaultIsNewestFirst() {
        get("alice", "/api/library", "kind=BOOK&sort=title")
                .body("items.book.title",
                        contains("Alpha pagination", "Bravo pagination", "Delta pagination"));

        // Default ordering: most recently added first, i.e. the seeding order reversed.
        get("alice", "/api/library", "kind=BOOK")
                .body("items.book.title",
                        contains("Bravo pagination", "Delta pagination", "Alpha pagination"));
    }

    @Test
    void sortAcceptsAnyCaseAndRejectsUnknownValues() {
        get("alice", "/api/library", "kind=BOOK&sort=TITLE")
                .body("items.book.title[0]", is("Alpha pagination"));

        given().auth().oauth2(token("alice"))
                .when().get("/api/library?sort=oops")
                .then().statusCode(400);
    }

    // ── Isolation ─────────────────────────────────────────────────────────────

    /**
     * Filters narrow, they never widen: whatever Bob combines, Alice's items stay out of
     * his pages — and the other way round.
     */
    @Test
    void noCombinationOfFiltersLeaksAnotherUsersItems() {
        String marker = "Leakera Fixtura";
        String aliceItem = addLibraryItem("alice", "BOOK", "Leak pagination", "READ", marker);
        String bobItem = addLibraryItem("bob", "BOOK", "Leak pagination", "READ", marker);

        for (String query : new String[] { "", "kind=BOOK", "status=READ", "sort=title",
                "sort=author", "page=0&size=200" }) {
            get("bob", "/api/library", marker, query)
                    .body("total", is(1))
                    .body("items.id", hasItem(bobItem))
                    .body("items.id", not(hasItem(aliceItem)));
            get("alice", "/api/library", marker, query)
                    .body("total", is(1))
                    .body("items.id", hasItem(aliceItem))
                    .body("items.id", not(hasItem(bobItem)));
        }
    }

    @Test
    void anItemIsFetchableByIdentifierOnlyByItsOwner() {
        String aliceItem = addLibraryItem("alice", "BOOK", "Deep link pagination", "OWNED",
                "Deeplinka Fixtura");

        given().auth().oauth2(token("alice"))
                .when().get("/api/library/" + aliceItem)
                .then().statusCode(200)
                .body("id", is(aliceItem))
                .body("book.title", is("Deep link pagination"));

        // Someone else's identifier answers 404, never 403: no existence leak.
        given().auth().oauth2(token("bob"))
                .when().get("/api/library/" + aliceItem)
                .then().statusCode(404);
    }

    // ── Wishlist ──────────────────────────────────────────────────────────────

    @Test
    void theWishlistIsPagedFilteredAndSortedToo() {
        get("alice", "/api/wishlist", "page=0&size=2")
                .body("items", hasSize(2))
                .body("page", is(0))
                .body("size", is(2))
                .body("total", is(5));

        get("alice", "/api/wishlist", "kind=MANGA")
                .body("total", is(2))
                .body("items.book.kind", everyItem(is("MANGA")));

        get("alice", "/api/wishlist", "priority=PRIORITY")
                .body("total", is(2))
                .body("items.priority", everyItem(is("PRIORITY")));

        get("alice", "/api/wishlist", "kind=BOOK&sort=title")
                .body("items.book.title",
                        contains("Alpha pagination", "Bravo pagination", "Delta pagination"));

        given().auth().oauth2(token("alice"))
                .when().get("/api/wishlist?sort=oops")
                .then().statusCode(400);
    }

    @Test
    void theWishlistOfAnotherUserStaysOutOfEveryPage() {
        get("bob", "/api/wishlist", "page=0&size=200").body("total", is(0));
    }
}
