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
 * not localhost, plus the two origins the native container sends (#527) — the Capacitor
 * WebView serves the bundle from {@code https://localhost} on Android and
 * {@code capacitor://localhost} on iOS, and those are the {@code Origin} values of every
 * call the installed app makes, the OIDC code exchange included.
 */
@QuarkusTest
@TestProfile(CorsTest.DeployedOriginProfile.class)
class CorsTest {

    static final String ALLOWED_ORIGIN = "https://librarius.example.org";
    static final String ANDROID_ORIGIN = "https://localhost";
    static final String IOS_ORIGIN = "capacitor://localhost";

    public static class DeployedOriginProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.http.cors.origins",
                    String.join(",", ALLOWED_ORIGIN, ANDROID_ORIGIN, IOS_ORIGIN));
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
                          "status": "TO_READ" }
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
                          "status": "TO_READ" }
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

    /**
     * #459: saving the profile is the one write sent as a PATCH, and the allow-list named every
     * method but that one — the browser's Origin header got the save refused while every other
     * write went through.
     */
    @Test
    void acceptsAProfileSaveCarryingTheDeployedOrigin() {
        given().auth().oauth2(keycloak.getAccessToken("alice"))
                .header("Origin", ALLOWED_ORIGIN)
                .contentType("application/json")
                .body("{ \"displayName\": \"alice\", \"locale\": \"fr\", \"publicAccount\": false }")
                .when().patch("/api/me")
                .then().statusCode(200);
    }

    /**
     * #527: the installed Android app's calls carry {@code https://localhost}. Without it on
     * the list the app signs in, then gets a 403 on everything it loads.
     */
    @Test
    void answersThePreflightFromTheAndroidContainer() {
        given().header("Origin", ANDROID_ORIGIN)
                .header("Access-Control-Request-Method", "GET")
                .when().options("/api/library")
                .then().statusCode(200)
                .header("Access-Control-Allow-Origin", ANDROID_ORIGIN);
    }

    /** The iOS container's origin is the other scheme, and is no more derivable than the first. */
    @Test
    void acceptsAWriteFromTheIosContainer() {
        given().auth().oauth2(keycloak.getAccessToken("alice"))
                .header("Origin", IOS_ORIGIN)
                .contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "CORS check from iOS", "authors": "Test" },
                          "status": "TO_READ" }
                        """)
                .when().post("/api/library")
                .then().statusCode(201);
    }

    @Test
    void answersThePreflightOfAPatch() {
        given().header("Origin", ALLOWED_ORIGIN)
                .header("Access-Control-Request-Method", "PATCH")
                .when().options("/api/me")
                .then().statusCode(200)
                .header("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("PATCH"));
    }
}
