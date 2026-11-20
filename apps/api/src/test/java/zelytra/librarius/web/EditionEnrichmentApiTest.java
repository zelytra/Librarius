package zelytra.librarius.web;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import zelytra.librarius.catalog.CatalogResult;
import zelytra.librarius.catalog.CatalogService;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Enriching the editions of a provider-sourced work with the other printings that provider
 * knows (#197). The provider is mocked: what is under test is the merge and its guards — a
 * work with no reference, a provider returning nothing, a provider that throws — not what any
 * real catalogue answers, which its own provider test covers.
 *
 * <p>Each test uses a title of its own: works are matched on (kind, title, authors, volume)
 * and the catalog is shared by the whole suite, so two tests sharing a title would gather
 * their editions under one work.
 */
@QuarkusTest
class EditionEnrichmentApiTest {

    private static final String AUTHOR = "Enrichment Test";

    @InjectMock
    CatalogService catalog;

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    /** Adds an owned title, provider reference and ISBN included, and returns its item id. */
    private String add(String user, String title, String provider, String ref, String isbn13) {
        return given().auth().oauth2(token(user)).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "%s", "authors": "%s",
                                    "publisher": "Pocket", "isbn13": %s,
                                    "provider": %s, "providerRef": %s },
                          "status": "TO_READ" }
                        """.formatted(title, AUTHOR, quoted(isbn13), quoted(provider), quoted(ref)))
                .when().post("/api/library")
                .then().statusCode(201)
                .extract().path("id");
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private String workId(String user, String itemId) {
        return given().auth().oauth2(token(user)).when().get("/api/library/" + itemId)
                .then().statusCode(200).extract().path("book.workId");
    }

    private Response editions(String user, String itemId) {
        return given().auth().oauth2(token(user))
                .when().get("/api/works/" + workId(user, itemId) + "/editions")
                .then().statusCode(200).extract().response();
    }

    private static CatalogResult providerEdition(String isbn13, String publisher, String cover) {
        return new CatalogResult("BOOK", null, null, null, cover, null, isbn13, publisher,
                "eng", null, "openlibrary", "OL" + publisher + "M");
    }

    // ── Merge ───────────────────────────────────────────────────────────────────

    /**
     * The point of the issue: a provider-sourced work shows the printings the provider knows
     * next to the one the user entered, each with its own cover, and none forced to own it.
     */
    @Test
    void mergesProviderEditionsAlongsideTheStoredOne() {
        Mockito.when(catalog.editionsOf(anyString(), anyString(), anyInt())).thenReturn(List.of(
                providerEdition("9780575081406", "Gollancz", "https://covers/gollancz.jpg"),
                providerEdition("9782266123456", "Folio", "https://covers/folio.jpg")));

        String item = add("alice", "Enrichissement - fusion", "openlibrary", "OL45804W", "9781111111117");

        editions("alice", item).then()
                .body("$", hasSize(3))
                .body("publisher", hasItem("Pocket"))
                .body("publisher", hasItem("Gollancz"))
                .body("publisher", hasItem("Folio"))
                // The stored one is owned and keyed by a row; the provider ones are neither.
                .body("find { it.publisher == 'Pocket' }.owned", is(true))
                .body("find { it.publisher == 'Gollancz' }.owned", is(false))
                .body("find { it.publisher == 'Gollancz' }.id", nullValue())
                .body("find { it.publisher == 'Gollancz' }.coverUrl", is("https://covers/gollancz.jpg"));
    }

    /**
     * #568: a title added from a search result carries no release date — Open Library's search
     * gives one date per printing of the whole work, BnF only a year. The provider's printing
     * with the same ISBN knows it: the stored edition takes the date from it, and keeps it.
     */
    @Test
    void theStoredEditionTakesItsReleaseDateFromTheProvidersPrintingOfTheSameIsbn() {
        Mockito.when(catalog.editionsOf(anyString(), anyString(), anyInt())).thenReturn(List.of(
                new CatalogResult("BOOK", null, null, null, null, null, "9781111111148", "Pocket",
                        "fr", java.time.LocalDate.of(2011, 9, 1), null, null, 662, "openlibrary", "OL7M"),
                new CatalogResult("BOOK", null, null, null, null, null, "9780000000031", "Other",
                        "en", java.time.LocalDate.of(2007, 3, 27), null, null, null, "openlibrary", "OL8M")));

        String item = add("alice", "Enrichissement - date", "openlibrary", "OL45809W", "9781111111148");
        given().auth().oauth2(token("alice")).when().get("/api/library/" + item)
                .then().body("book.releaseDate", nullValue());

        editions("alice", item).then()
                .body("find { it.owned }.releaseDate", is("2011-09-01"))
                // The other printing keeps its own date, and is not mistaken for the reader's.
                .body("find { it.publisher == 'Other' }.releaseDate", is("2007-03-27"));

        // Stored, not only shown: the book itself now says when it came out.
        Mockito.when(catalog.editionsOf(anyString(), anyString(), anyInt())).thenReturn(List.of());
        given().auth().oauth2(token("alice")).when().get("/api/library/" + item)
                .then().body("book.releaseDate", is("2011-09-01"));
    }

    /** A date the stored edition already has is never overwritten by a provider's. */
    @Test
    void aStoredReleaseDateIsKept() {
        Mockito.when(catalog.editionsOf(anyString(), anyString(), anyInt())).thenReturn(List.of(
                new CatalogResult("BOOK", null, null, null, null, null, "9781111111155", "Pocket",
                        "fr", java.time.LocalDate.of(1999, 1, 1), null, null, null, "openlibrary", "OL9M")));
        String item = given().auth().oauth2(token("alice")).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "Enrichissement - date gardée", "authors": "%s",
                                    "isbn13": "9781111111155", "releaseDate": "2012-05-02",
                                    "provider": "openlibrary", "providerRef": "OL45810W" },
                          "status": "TO_READ" }
                        """.formatted(AUTHOR))
                .when().post("/api/library").then().statusCode(201).extract().path("id");

        editions("alice", item).then().body("find { it.owned }.releaseDate", is("2012-05-02"));
    }

    /**
     * #480: each printing carries what tells it apart — its length and its date — and the
     * list leads with the reader's own, then the French printings, then those with a cover.
     */
    @Test
    void listsPrintingsWithTheirMetadataTheReadersOwnAndTheFrenchOnesFirst() {
        Mockito.when(catalog.editionsOf(anyString(), anyString(), anyInt())).thenReturn(List.of(
                new CatalogResult("BOOK", null, null, null, null, null, "9780000000017", "Bare",
                        "en", null, null, null, null, "openlibrary", "OL1M"),
                new CatalogResult("BOOK", null, null, null, "https://covers/eng.jpg", null, "9780000000024", "Cover",
                        "en", null, null, null, 320, "openlibrary", "OL2M"),
                new CatalogResult("BOOK", null, null, null, null, null, "9782000000013", "Francais",
                        "fr", java.time.LocalDate.of(2006, 1, 1), null, null, 1046, "openlibrary", "OL3M")));

        String item = add("alice", "Enrichissement - ordre", "openlibrary", "OL45805W", "9781111111124");

        editions("alice", item).then()
                .body("publisher", contains("Pocket", "Francais", "Cover", "Bare"))
                .body("find { it.publisher == 'Francais' }.pageCount", is(1046))
                .body("find { it.publisher == 'Francais' }.releaseDate", is("2006-01-01"));
    }

    /**
     * A printing the user already entered and the provider also returns is one printing, not
     * two: the provider copy is dropped on the shared ISBN.
     */
    @Test
    void deduplicatesAProviderEditionAgainstAStoredIsbn() {
        String isbn = "9782221252017";
        Mockito.when(catalog.editionsOf(anyString(), anyString(), anyInt())).thenReturn(List.of(
                providerEdition(isbn, "Pocket", "https://covers/dup.jpg"),
                providerEdition("9780575081406", "Gollancz", "https://covers/g.jpg")));

        String item = add("alice", "Enrichissement - doublon", "openlibrary", "OL77W", isbn);

        editions("alice", item).then()
                // The stored ISBN and its provider echo collapse; only the new printing is added.
                .body("$", hasSize(2))
                .body("publisher", hasItem("Pocket"))
                .body("publisher", hasItem("Gollancz"));
    }

    // ── Guards ──────────────────────────────────────────────────────────────────

    /**
     * A work with no provider reference — a hand-typed entry, or one predating V12 — behaves
     * exactly as before: the stored editions alone, and no provider ever asked.
     */
    @Test
    void aWorkWithNoReferenceIsNotEnriched() {
        Mockito.when(catalog.editionsOf(anyString(), anyString(), anyInt())).thenReturn(List.of(
                providerEdition("9780575081406", "Gollancz", "https://covers/g.jpg")));

        String item = add("alice", "Enrichissement - sans référence", null, null, "9782070360024");

        editions("alice", item).then()
                .body("$", hasSize(1))
                .body("[0].publisher", is("Pocket"))
                .body("[0].owned", is(true))
                // The provider was never consulted, so its canned edition is nowhere in sight.
                .body("publisher", not(hasItem("Gollancz")));
    }

    /**
     * A provider that knows no other edition — the ordinary case, and Open Library's until it
     * hands out a usable work key — leaves the stored list untouched, never an error.
     */
    @Test
    void aProviderReturningNothingLeavesTheStoredEditions() {
        Mockito.when(catalog.editionsOf(anyString(), anyString(), anyInt())).thenReturn(List.of());

        String item = add("alice", "Enrichissement - rien", "openlibrary", "OL99W", "9782070360031");

        editions("alice", item).then()
                .body("$", hasSize(1))
                .body("[0].owned", is(true));
    }

    /**
     * A provider that is down or misbehaving must not fail a detail screen: the read swallows
     * the failure and answers the stored editions, a 200 rather than a 500.
     */
    @Test
    void aProviderThatFailsDoesNotBreakTheEndpoint() {
        Mockito.when(catalog.editionsOf(anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("provider down"));

        String item = add("alice", "Enrichissement - panne", "openlibrary", "OL13W", "9782070360048");

        editions("alice", item).then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].owned", is(true));
    }

    /**
     * Enrichment is catalog data laid over an owned work; it does not widen who may read it.
     * A work Bob owns nothing of is a 404 for him, provider reference or not — exactly as the
     * unenriched endpoint already answers.
     */
    @Test
    void enrichmentDoesNotOpenAWorkToAUserWhoOwnsNothingOfIt() {
        Mockito.when(catalog.editionsOf(anyString(), anyString(), anyInt())).thenReturn(List.of(
                providerEdition("9780575081406", "Gollancz", "https://covers/g.jpg")));

        String aliceItem = add("alice", "Enrichissement - cloisonné", "openlibrary", "OL21W", "9782070360055");
        String work = workId("alice", aliceItem);

        given().auth().oauth2(token("bob"))
                .when().get("/api/works/" + work + "/editions")
                .then().statusCode(404);
    }

    /** Every provider edition is unowned: ownership is a row, and these are not rows. */
    @Test
    void providerEditionsAreNeverMarkedOwned() {
        Mockito.when(catalog.editionsOf(anyString(), anyString(), anyInt())).thenReturn(List.of(
                providerEdition("9780575081406", "Gollancz", "https://covers/g.jpg"),
                providerEdition("9782266123456", "Folio", "https://covers/f.jpg")));

        String item = add("alice", "Enrichissement - jamais possédée", "openlibrary", "OL34W", "9782070360062");

        editions("alice", item).then()
                .body("findAll { it.id == null }", hasSize(2))
                .body("findAll { it.id == null }.owned", everyItem(is(false)));
    }
}
