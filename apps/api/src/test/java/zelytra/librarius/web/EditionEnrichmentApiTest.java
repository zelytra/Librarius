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
                          "status": "OWNED" }
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
