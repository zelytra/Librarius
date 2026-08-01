package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

/**
 * The user-to-user follow resource (#200): {@code PUT}/{@code DELETE
 * /api/users/{id}/follow} and the caller's own {@code /api/me/following} and
 * {@code /api/me/followers} lists.
 *
 * <p>This is the social follow between two accounts, distinct from the series and author
 * follows: it is the first relationship the schema draws between two {@code app_user} rows.
 */
@QuarkusTest
class UserFollowApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    /** The user's own identifier, read from the profile — provisions the account on the way. */
    private String id(String user) {
        return given().auth().oauth2(token(user))
                .when().get("/api/me")
                .then().statusCode(200)
                .extract().path("id");
    }

    /** Following round-trips, is idempotent, and both lists reflect it immediately. */
    @Test
    void followRoundTripsAndIsIdempotent() {
        String alice = id("alice");
        String bob = id("bob");

        // A repeated PUT must not fail on the primary key.
        given().auth().oauth2(token("alice")).when().put("/api/users/" + bob + "/follow")
                .then().statusCode(204);
        given().auth().oauth2(token("alice")).when().put("/api/users/" + bob + "/follow")
                .then().statusCode(204);

        given().auth().oauth2(token("alice"))
                .when().get("/api/me/following")
                .then().statusCode(200)
                .body("id", hasItem(bob));
        given().auth().oauth2(token("bob"))
                .when().get("/api/me/followers")
                .then().statusCode(200)
                .body("id", hasItem(alice));

        // A repeated DELETE is idempotent too, and clears the edge from both lists.
        given().auth().oauth2(token("alice")).when().delete("/api/users/" + bob + "/follow")
                .then().statusCode(204);
        given().auth().oauth2(token("alice")).when().delete("/api/users/" + bob + "/follow")
                .then().statusCode(204);

        given().auth().oauth2(token("alice"))
                .when().get("/api/me/following")
                .then().statusCode(200)
                .body("id", not(hasItem(bob)));
        given().auth().oauth2(token("bob"))
                .when().get("/api/me/followers")
                .then().statusCode(200)
                .body("id", not(hasItem(alice)));
    }

    /** A user cannot follow themselves: refused with 400, on both verbs. */
    @Test
    void followingYourselfIsRejected() {
        String alice = id("alice");

        given().auth().oauth2(token("alice")).when().put("/api/users/" + alice + "/follow")
                .then().statusCode(400);
        given().auth().oauth2(token("alice")).when().delete("/api/users/" + alice + "/follow")
                .then().statusCode(400);
    }

    /** Following an id that is nobody is a 404, never a 403 that would confirm nothing. */
    @Test
    void followingAnUnknownUserIsNotFound() {
        String unknown = UUID.randomUUID().toString();

        given().auth().oauth2(token("alice")).when().put("/api/users/" + unknown + "/follow")
                .then().statusCode(404);
        given().auth().oauth2(token("alice")).when().delete("/api/users/" + unknown + "/follow")
                .then().statusCode(404);
    }

    /**
     * Isolation: bob's follow of alice is bob's and alice's business, never carol's. It shows
     * in bob's following and alice's followers, and in neither of carol's lists.
     */
    @Test
    void oneUsersFollowNeverLeaksIntoAnothersLists() {
        String alice = id("alice");
        String bob = id("bob");
        String carol = id("carol");

        given().auth().oauth2(token("bob")).when().put("/api/users/" + alice + "/follow")
                .then().statusCode(204);

        // The two parties see it.
        given().auth().oauth2(token("bob"))
                .when().get("/api/me/following")
                .then().statusCode(200)
                .body("id", hasItem(alice));
        given().auth().oauth2(token("alice"))
                .when().get("/api/me/followers")
                .then().statusCode(200)
                .body("id", hasItem(bob));

        // Carol sees nothing of it, on either side.
        given().auth().oauth2(token("carol"))
                .when().get("/api/me/following")
                .then().statusCode(200)
                .body("id", not(hasItem(alice)))
                .body("id", not(hasItem(bob)));
        given().auth().oauth2(token("carol"))
                .when().get("/api/me/followers")
                .then().statusCode(200)
                .body("id", not(hasItem(alice)))
                .body("id", not(hasItem(bob)));

        // Leave the shared account state as it was found.
        given().auth().oauth2(token("bob")).when().delete("/api/users/" + alice + "/follow")
                .then().statusCode(204);
    }
}
