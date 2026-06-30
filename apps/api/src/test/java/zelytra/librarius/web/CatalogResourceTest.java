package zelytra.librarius.web;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import zelytra.librarius.catalog.CatalogResult;
import zelytra.librarius.catalog.CatalogService;
import zelytra.librarius.domain.Kind;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

/** Vérifie l'endpoint de recherche (auth + forme) avec un CatalogService mocké. */
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
        Mockito.when(catalog.search(Kind.BOOK, "wing", 20)).thenReturn(List.of(
                new CatalogResult("BOOK", "Fourth Wing", "Rebecca Yarros", 2023,
                        "https://cover", "synopsis", "9781234567890", "Piatkus", "fr", null,
                        "openlibrary", "ref-1")));

        given().auth().oauth2(keycloak.getAccessToken("alice"))
                .when().get("/api/catalog/search?q=wing&kind=BOOK")
                .then().statusCode(200)
                .body("[0].title", is("Fourth Wing"))
                .body("[0].provider", is("openlibrary"));
    }
}
