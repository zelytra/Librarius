package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;

/**
 * The Home screen's layout (#54): {@code GET}/{@code PUT /api/dashboard/layout}.
 *
 * <p>Runs as {@code alice}, an account the whole suite shares — so every assertion here is
 * either about the shape of the answer (true whatever she saved before) or about a value
 * this test itself just wrote, never about "the very first thing she ever sees". See
 * {@code DashboardLayoutServiceTest} for the default-layout arithmetic in isolation, and
 * {@code DataIsolationTest} for the cross-user case.
 */
@QuarkusTest
class DashboardLayoutApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String token() {
        return keycloak.getAccessToken("alice");
    }

    @Test
    void requiresAuthentication() {
        given().when().get("/api/dashboard/layout").then().statusCode(401);
        given().contentType("application/json").body("{ \"sections\": [] }")
                .when().put("/api/dashboard/layout").then().statusCode(401);
    }

    /** Whatever was saved before, the answer always names every known section once. */
    @Test
    void getAlwaysReturnsEveryKnownSectionExactlyOnce() {
        given().auth().oauth2(token())
                .when().get("/api/dashboard/layout")
                .then().statusCode(200)
                .body("sections.code", containsInAnyOrder(
                        "resumeReading", "counters", "goal", "upcoming", "recentlyRead"));
    }

    /** An empty body resets to the default order, all sections visible — see #54. */
    @Test
    void puttingAnEmptyListResetsToTheDefaultOrder() {
        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"sections\": [] }")
                .when().put("/api/dashboard/layout")
                .then().statusCode(200)
                .body("sections.code",
                        is(java.util.List.of("resumeReading", "counters", "goal", "upcoming", "recentlyRead")))
                .body("sections.hidden", everyItem(is(false)));
    }

    /** A save is not an accident: it survives the request that made it and the next GET. */
    @Test
    void aSavedOrderAndHiddenFlagSurviveASubsequentGet() {
        given().auth().oauth2(token()).contentType("application/json")
                .body("""
                        { "sections": [
                            { "code": "goal", "hidden": true },
                            { "code": "counters", "hidden": false },
                            { "code": "resumeReading", "hidden": false },
                            { "code": "upcoming", "hidden": true },
                            { "code": "recentlyRead", "hidden": false }
                        ] }
                        """)
                .when().put("/api/dashboard/layout")
                .then().statusCode(200)
                .body("sections.code", is(java.util.List.of(
                        "goal", "counters", "resumeReading", "upcoming", "recentlyRead")));

        given().auth().oauth2(token())
                .when().get("/api/dashboard/layout")
                .then().statusCode(200)
                .body("sections.code", is(java.util.List.of(
                        "goal", "counters", "resumeReading", "upcoming", "recentlyRead")))
                .body("sections.find { it.code == 'goal' }.hidden", is(true))
                .body("sections.find { it.code == 'upcoming' }.hidden", is(true))
                .body("sections.find { it.code == 'counters' }.hidden", is(false));
    }

    /**
     * A layout saved before a section existed still works once one ships: the acceptance
     * criterion of #54, exercised through the real endpoint rather than only on
     * {@code normalize} in isolation.
     */
    @Test
    void aPartialSaveIsCompletedWithTheMissingSectionsAppendedVisible() {
        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"sections\": [ { \"code\": \"recentlyRead\", \"hidden\": false } ] }")
                .when().put("/api/dashboard/layout")
                .then().statusCode(200)
                .body("sections.code", containsInAnyOrder(
                        "resumeReading", "counters", "goal", "upcoming", "recentlyRead"))
                .body("sections[0].code", is("recentlyRead"));
    }

    /** A code the API no longer recognises is dropped, not rejected. */
    @Test
    void anUnknownCodeInThePutBodyIsSilentlyDropped() {
        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"sections\": [ { \"code\": \"retiredSection\", \"hidden\": false } ] }")
                .when().put("/api/dashboard/layout")
                .then().statusCode(200)
                .body("sections.code", containsInAnyOrder(
                        "resumeReading", "counters", "goal", "upcoming", "recentlyRead"));
    }

    @Test
    void rejectsAMissingSectionsField() {
        given().auth().oauth2(token()).contentType("application/json")
                .body("{}")
                .when().put("/api/dashboard/layout")
                .then().statusCode(400);
    }

    @Test
    void rejectsABlankSectionCode() {
        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"sections\": [ { \"code\": \"\", \"hidden\": false } ] }")
                .when().put("/api/dashboard/layout")
                .then().statusCode(400);
    }
}
