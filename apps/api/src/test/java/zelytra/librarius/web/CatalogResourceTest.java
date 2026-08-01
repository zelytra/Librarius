package zelytra.librarius.web;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import zelytra.librarius.catalog.CatalogQuery;
import zelytra.librarius.catalog.CatalogResult;
import zelytra.librarius.catalog.CatalogService;
import zelytra.librarius.domain.Kind;

import java.util.List;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;

/** Checks the search endpoint (auth and payload shape) with a mocked CatalogService. */
@QuarkusTest
class CatalogResourceTest {

    @InjectMock
    CatalogService catalog;

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    @Test
    void searchRequiresAuthentication() {
        given().when().get("/api/catalog/search?q=wing").then().statusCode(401);
    }

    @Test
    void searchReturnsMappedResults() {
        Mockito.when(catalog.search(Set.of(Kind.BOOK), CatalogQuery.of("wing"), 20)).thenReturn(List.of(
                new CatalogResult("BOOK", "Fourth Wing", "Rebecca Yarros", 2023,
                        "https://cover", "synopsis", "9781234567890", "Piatkus", "fr", null,
                        "openlibrary", "ref-1")));

        given().auth().oauth2(keycloak.getAccessToken("alice"))
                .when().get("/api/catalog/search?q=wing&kind=BOOK")
                .then().statusCode(200)
                .body("[0].title", is("Fourth Wing"))
                .body("[0].provider", is("openlibrary"));
    }

    @Test
    void searchWithoutAKindSpansEveryMedium() {
        // No kind on the request: the resource asks the service for every registered kind
        // (an empty set) and returns the merged, cross-medium answer.
        Mockito.when(catalog.search(Set.of(), CatalogQuery.of("mixed"), 20)).thenReturn(List.of(
                new CatalogResult("BOOK", "Fourth Wing", "Rebecca Yarros", 2023, null, null,
                        null, null, null, null, "openlibrary", null),
                new CatalogResult("MANGA", "One Piece", "Eiichiro Oda", 1997, null, null,
                        null, null, null, null, "anilist", null)));

        given().auth().oauth2(keycloak.getAccessToken("alice"))
                .when().get("/api/catalog/search?q=mixed")
                .then().statusCode(200)
                .body("title", hasItems("Fourth Wing", "One Piece"))
                .body("kind", hasItems("BOOK", "MANGA"));
    }

    @Test
    void searchNarrowsToTheKindsNamed() {
        // Two kinds named on one call: the repeatable kind param carries both to the service.
        Mockito.when(catalog.search(Set.of(Kind.BOOK, Kind.MANGA), CatalogQuery.of("mixed"), 20))
                .thenReturn(List.of(new CatalogResult("MANGA", "One Piece", "Eiichiro Oda", 1997,
                        null, null, null, null, null, null, "anilist", null)));

        given().auth().oauth2(keycloak.getAccessToken("alice"))
                .when().get("/api/catalog/search?q=mixed&kind=BOOK&kind=MANGA")
                .then().statusCode(200)
                .body("[0].title", is("One Piece"));
    }

    @Test
    void searchCarriesTheAdvancedCriteria() {
        CatalogQuery expected =
                new CatalogQuery("dune", "Frank Herbert", 1965, "fr", "Pocket", null);
        Mockito.when(catalog.search(Set.of(Kind.BOOK), expected, 20)).thenReturn(List.of(
                new CatalogResult("BOOK", "Dune", "Frank Herbert", 1965, null, null,
                        null, "Pocket", "fre", null, "openlibrary", null)));

        // Through queryParam rather than a hand-written query string: RestAssured re-encodes
        // what it is handed, so a "Frank+Herbert" written inline reaches the resource as a
        // literal plus and the criterion no longer matches.
        given().auth().oauth2(keycloak.getAccessToken("alice"))
                .queryParam("q", "dune")
                .queryParam("author", "Frank Herbert")
                .queryParam("year", 1965)
                .queryParam("language", "fr")
                .queryParam("publisher", "Pocket")
                .queryParam("kind", "BOOK")
                .when().get("/api/catalog/search")
                .then().statusCode(200)
                .body("[0].title", is("Dune"));
    }

    @Test
    void searchAcceptsAnIsbnWithoutAnyFreeText() {
        // Scanning or pasting an ISBN is a search on its own, not a keyword.
        CatalogQuery expected =
                new CatalogQuery(null, null, null, null, null, "9780441013593");
        Mockito.when(catalog.search(Set.of(Kind.BOOK), expected, 20)).thenReturn(List.of(
                new CatalogResult("BOOK", "Dune", "Frank Herbert", 1965, null, null,
                        "9780441013593", null, null, null, "openlibrary", null)));

        given().auth().oauth2(keycloak.getAccessToken("alice"))
                .when().get("/api/catalog/search?isbn=9780441013593&kind=BOOK")
                .then().statusCode(200)
                .body("[0].isbn13", is("9780441013593"));
    }

    @Test
    void searchWithoutAnyCriterionAnswersAnEmptyList() {
        // A whitespace-only field is no search at all: it must not reach a provider, and it
        // must not be charged to the caller's quota either.
        given().auth().oauth2(keycloak.getAccessToken("alice"))
                .queryParam("q", "   ")
                .when().get("/api/catalog/search")
                .then().statusCode(200)
                .body("size()", is(0));
    }
}
