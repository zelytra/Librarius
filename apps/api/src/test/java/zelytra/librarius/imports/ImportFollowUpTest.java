package zelytra.librarius.imports;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import zelytra.librarius.catalog.CatalogResult;
import zelytra.librarius.catalog.CatalogService;
import zelytra.librarius.catalog.provider.BnfProvider;
import zelytra.librarius.catalog.provider.BnfProvider.ListPrice;
import zelytra.librarius.domain.Edition;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

/**
 * #425: right after an import, the importing reader's editions are completed from the catalog
 * and then priced from the BnF, in the same pass — the ISBN the enricher finds is what the
 * price lookup needs. Both outside services are mocked.
 */
@QuarkusTest
class ImportFollowUpTest {

    private static final String ISBN = "9782070464241";

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    @InjectMock
    CatalogService catalog;

    @InjectMock
    BnfProvider bnf;

    @Inject
    ImportFollowUp followUp;

    @Inject
    EntityManager em;

    @Test
    void anImportedTitleGetsItsLengthIsbnAndPriceInOnePass() {
        String title = "Follow-up Horde " + UUID.randomUUID();
        String token = keycloak.getAccessToken("alice");
        String editionId = given().auth().oauth2(token).contentType("application/json")
                .body("{ \"book\": { \"kind\": \"BOOK\", \"title\": \"" + title
                        + "\", \"authors\": \"Alain Damasio\" }, \"status\": \"READ\" }")
                .when().post("/api/library")
                .then().statusCode(201)
                .extract().path("book.editionId");
        String aliceId = given().auth().oauth2(token).when().get("/api/me")
                .then().statusCode(200).extract().path("id");

        Mockito.when(catalog.search(any(), any(), anyInt())).thenReturn(List.of(new CatalogResult(
                "BOOK", title, "Alain Damasio", 2004, null, null, ISBN, "Folio", "fre", null, null,
                null, 736, "openlibrary", "OL1W")));
        Mockito.when(bnf.priceByIsbn(eq(ISBN)))
                .thenReturn(Optional.of(new ListPrice(new BigDecimal("12.40"), "EUR")));

        followUp.refresh(aliceId);

        Edition after = QuarkusTransaction.requiringNew()
                .call(() -> em.find(Edition.class, UUID.fromString(editionId)));
        assertEquals(Integer.valueOf(736), after.pageCount);
        assertEquals(ISBN, after.isbn13);
        assertEquals(new BigDecimal("12.40"), after.listPrice);
    }
}
