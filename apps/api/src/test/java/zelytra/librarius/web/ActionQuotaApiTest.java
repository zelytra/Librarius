package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;

/**
 * #583: the costly actions a caller may start in an hour. Shown on the error notices, the
 * cheapest to send; the import, export and report endpoints share the same guard.
 */
@QuarkusTest
@TestProfile(ActionQuotaApiTest.LowQuota.class)
class ActionQuotaApiTest {

    /** Two error notices an hour, the other quotas as low, so a test reaches them quickly. */
    public static class LowQuota implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "librarius.quota.errors.per-hour", "2",
                    "librarius.quota.export.per-hour", "1");
        }
    }

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private io.restassured.response.ValidatableResponse error(String user) {
        return given().auth().oauth2(keycloak.getAccessToken(user)).contentType("application/json")
                .body(Map.of("message", "boom from " + user))
                .when().post("/api/feedback/errors").then();
    }

    @Test
    void refusesTheCallPastTheQuotaWithARetryDelayAndLeavesOthersAlone() {
        error("carol").statusCode(204);
        error("carol").statusCode(204);
        error("carol").statusCode(429)
                .header("Retry-After", notNullValue())
                .body("message", containsString("Trop"));
        // Another reader has a quota of their own.
        error("bob").statusCode(204);
    }

    @Test
    void appliesToExportsToo() {
        String token = keycloak.getAccessToken("alice");
        given().auth().oauth2(token).queryParam("format", "csv").when().get("/api/export").then().statusCode(200);
        given().auth().oauth2(token).queryParam("format", "csv").when().get("/api/export").then().statusCode(429);
    }
}
