package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

/**
 * #585: the handle of a scrape is concatenated into the provider's URL. Anything but a
 * pseudo — a path, a query, a line break that would forge a log line — is refused before a
 * job is started, with a message the reader can act on.
 */
@QuarkusTest
class ImportHandleApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    @ParameterizedTest
    @ValueSource(strings = {"a/../x", "a?page=9", "a#b", "a\nforged", "https://booknode.com/profil/x", " ", ""})
    void refusesAHandleThatIsNotAPseudo(String handle) {
        given().auth().oauth2(keycloak.getAccessToken("bob")).contentType("application/json")
                .body(Map.of("handle", handle))
                .when().post("/api/import/booknode")
                .then().statusCode(400)
                .body("message", containsString("pseudo"));
    }
}
