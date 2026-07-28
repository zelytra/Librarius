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

import static io.restassured.RestAssured.given;
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
        Mockito.when(catalog.search(Kind.BOOK, CatalogQuery.of("wing"), 20)).thenReturn(List.of(
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
    void searchCarriesTheAdvancedCriteria() {
        CatalogQuery expected =
                new CatalogQuery("dune", "Frank Herbert", 1965, "fr", "Pocket", null);
        Mockito.when(catalog.search(Kind.BOOK, expected, 20)).thenReturn(List.of(
                new CatalogResult("BOOK", "Dune", "Frank Herbert", 1965, null, null,
                        null, "Pocket", "fre", null, "openlibrary", null)));

        given().auth().oauth2(keycloak.getAccessToken("alice"))
                .when().get("/api/catalog/search?q=dune&author=Frank+Herbert&year=1965"
                        + "&language=fr&publisher=Pocket&kind=BOOK")
                .then().statusCode(200)
                .body("[0].title", is("Dune"));
    }

    @Test
    void searchAcceptsAnIsbnWithoutAnyFreeText() {
        // Scanning or pasting an ISBN is a search on its own, not a keyword.
        CatalogQuery expected =
                new CatalogQuery(null, null, null, null, null, "9780441013593");
        Mockito.when(catalog.search(Kind.BOOK, expected, 20)).thenReturn(List.of(
                new CatalogResult("BOOK", "Dune", "Frank Herbert", 1965, null, null,
                        "9780441013593", null, null, null, "openlibrary", null)));

        given().auth().oauth2(keycloak.getAccessToken("alice"))
                .when().get("/api/catalog/search?isbn=9780441013593&kind=BOOK")
                .then().statusCode(200)
                .body("[0].isbn13", is("9780441013593"));
    }

    @Test
    void searchWithoutAnyCriterionAnswersAnEmptyList() {
        given().auth().oauth2(keycloak.getAccessToken("alice"))
                .when().get("/api/catalog/search?q=+")
                .then().statusCode(200)
                .body("size()", is(0));
    }
}
