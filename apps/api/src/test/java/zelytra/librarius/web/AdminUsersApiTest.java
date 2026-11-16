package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zelytra.librarius.account.DatabaseProbe;
import zelytra.librarius.account.KeycloakAccountDeleter.Outcome;
import zelytra.librarius.account.RecordingKeycloakAccountDeleter;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static zelytra.librarius.web.SocialTestSupport.id;
import static zelytra.librarius.web.SocialTestSupport.token;

/** #499: the admins' list of members, and deleting one with everything they own. alice is the admin. */
@QuarkusTest
class AdminUsersApiTest {

    @Inject
    RecordingKeycloakAccountDeleter identityProvider;

    @Inject
    DatabaseProbe probe;

    @BeforeEach
    void reset() {
        identityProvider.reset();
    }

    @AfterEach
    void nominal() {
        identityProvider.reset();
    }

    private static JsonPath users(String query) {
        var request = given().auth().oauth2(token("alice")).queryParam("size", 100);
        if (query != null) {
            request.queryParam("q", query);
        }
        return request.when().get("/api/admin/users").then().statusCode(200).extract().jsonPath();
    }

    @Test
    void listsTheMembersWithTheirUsernameAndCreationDate() {
        String bob = id("bob");
        Map<String, Object> entry = users(null).getMap("items.find { it.id == '" + bob + "' }");

        assertNotNull(entry);
        assertEquals("bob", entry.get("username"));
        assertNotNull(entry.get("createdAt"));
        assertNotNull(entry.get("displayName"));
        assertTrue(users("bo").getList("items.username").contains("bob"));
        assertTrue(!users("zzz-nobody").getList("items.username").contains("bob"));
    }

    @Test
    void deletesAMemberWithAllTheirDataAndTheirKeycloakAccount() {
        String carol = id("carol");
        given().auth().oauth2(token("carol")).contentType("application/json")
                .body("{ \"book\": { \"kind\": \"BOOK\", \"title\": \"À effacer\" }, \"status\": \"READ\" }")
                .when().post("/api/library").then().statusCode(201);

        given().auth().oauth2(token("alice")).when().delete("/api/admin/users/" + carol).then().statusCode(204);

        assertEquals(List.of(carol), identityProvider.deletedSubjects());
        probe.personalRows(carol).forEach((table, rows) ->
                assertEquals(0L, rows, "rows left in " + table + " for the deleted member"));
    }

    @Test
    void nothingIsErasedWhenKeycloakRefuses() {
        String carol = id("carol");
        identityProvider.willAnswer(Outcome.FAILED);

        given().auth().oauth2(token("alice")).when().delete("/api/admin/users/" + carol).then().statusCode(503);

        assertEquals(1L, probe.personalRows(carol).get("app_user"));
    }

    @Test
    void onlyAnAdminManagesMembersAndNeverDeletesThemselfHere() {
        given().auth().oauth2(token("bob")).when().get("/api/admin/users").then().statusCode(403);
        given().auth().oauth2(token("bob")).when().delete("/api/admin/users/" + id("carol")).then().statusCode(403);
        given().auth().oauth2(token("alice")).when().delete("/api/admin/users/" + id("alice")).then().statusCode(400);
        given().auth().oauth2(token("alice")).when().delete("/api/admin/users/nobody").then().statusCode(404);
        assertTrue(identityProvider.deletedSubjects().isEmpty());
    }
}
