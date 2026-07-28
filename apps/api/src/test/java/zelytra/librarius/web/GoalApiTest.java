package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * Yearly reading goals.
 *
 * <p>This resource had no test coverage and is not exposed by any screen: creating a goal
 * failed in the database with nothing reporting it (the entity was persisted before
 * {@code target_count}, a NOT NULL column, had been set).
 */
@QuarkusTest
class GoalApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String token() {
        return keycloak.getAccessToken("alice");
    }

    @Test
    void requiresAuthentication() {
        given().contentType("application/json")
                .body("{ \"targetCount\": 12 }")
                .when().put("/api/goals/2980")
                .then().statusCode(401);
    }

    @Test
    void createsGoalForYear() {
        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"targetCount\": 24, \"unit\": \"BOOKS\" }")
                .when().put("/api/goals/2981")
                .then().statusCode(200)
                .body("year", is(2981))
                .body("targetCount", is(24))
                .body("unit", is("BOOKS"));

        given().auth().oauth2(token())
                .when().get("/api/goals")
                .then().statusCode(200)
                .body("find { it.year == 2981 }.targetCount", is(24));
    }

    /** A second call on the same year updates instead of creating a duplicate. */
    @Test
    void updatesExistingGoalInPlace() {
        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"targetCount\": 30, \"unit\": \"BOOKS\" }")
                .when().put("/api/goals/2982")
                .then().statusCode(200);

        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"targetCount\": 45, \"unit\": \"VOLUMES\" }")
                .when().put("/api/goals/2982")
                .then().statusCode(200)
                .body("targetCount", is(45))
                .body("unit", is("VOLUMES"));

        given().auth().oauth2(token())
                .when().get("/api/goals")
                .then().statusCode(200)
                .body("findAll { it.year == 2982 }.size()", is(1))
                .body("find { it.year == 2982 }.targetCount", is(45));
    }

    /** The unit is optional and falls back to BOOKS. */
    @Test
    void defaultsUnitToBooks() {
        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"targetCount\": 7 }")
                .when().put("/api/goals/2983")
                .then().statusCode(200)
                .body("unit", is("BOOKS"));
    }

    @Test
    void rejectsMissingTargetCount() {
        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"unit\": \"BOOKS\" }")
                .when().put("/api/goals/2984")
                .then().statusCode(400);
    }

    @Test
    void rejectsNonPositiveTargetCount() {
        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"targetCount\": 0 }")
                .when().put("/api/goals/2985")
                .then().statusCode(400);
    }
}
