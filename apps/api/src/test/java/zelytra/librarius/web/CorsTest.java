package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Guards the CORS allow-list against the regression that broke every write on the
 * deployed environment.
 *
 * <p>The deployment is same-origin — one host, the ingress routing {@code /api} to the API
 * and {@code /} to the PWA — which reads as "CORS does not apply". It does: browsers send
 * an {@code Origin} header on POST, PUT and DELETE even same-origin. With the allow-list
 * pinned to the Vite dev server, adding a title, changing a status or importing a library
 * all came back 403 on `librarius.zelytra.fr`, while reads kept working — which is why it
 * went unnoticed.
 *
 * <p>The profile below stands in for the deployed configuration: an allowed origin that is
 * not localhost.
 */
@QuarkusTest
@TestProfile(CorsTest.DeployedOriginProfile.class)
class CorsTest {

    static final String ALLOWED_ORIGIN = "https://librarius.example.org";

    public static class DeployedOriginProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.http.cors.origins", ALLOWED_ORIGIN);
        }
    }

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    @Test
    void acceptsAWriteCarryingTheDeployedOrigin() {
        given().auth().oauth2(keycloak.getAccessToken("alice"))
                .header("Origin", ALLOWED_ORIGIN)
                .contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "CORS check", "authors": "Test" },
                          "status": "OWNED" }
                        """)
                .when().post("/api/library")
                .then().statusCode(201);
    }

    @Test
    void rejectsAWriteFromAnUnknownOrigin() {
        given().auth().oauth2(keycloak.getAccessToken("alice"))
                .header("Origin", "https://attacker.example.net")
                .contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "Should not be created", "authors": "Test" },
                          "status": "OWNED" }
                        """)
                .when().post("/api/library")
                .then().statusCode(403);
    }

    @Test
    void answersThePreflightForTheDeployedOrigin() {
        given().header("Origin", ALLOWED_ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .when().options("/api/library")
                .then().statusCode(200)
                .header("Access-Control-Allow-Origin", ALLOWED_ORIGIN);
    }
}
