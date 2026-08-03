package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

/**
 * The user-to-user block resource (#203): {@code PUT}/{@code DELETE /api/users/{id}/block} and
 * the caller's own {@code /api/me/blocked} list.
 *
 * <p>A block is stored one-directionally but hides content both ways and overrides a follow.
 * These tests pin the round-trip, idempotence, self-refusal, the unknown-id 404, the
 * alice/bob/carol isolation of the blocked list, and the block-overrides-follow rule.
 */
@QuarkusTest
class UserBlockApiTest {

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

    private void unblock(String actor, String target) {
        given().auth().oauth2(token(actor)).when().delete("/api/users/" + target + "/block")
                .then().statusCode(204);
    }

    /** Blocking round-trips, is idempotent, and the blocked list reflects it immediately. */
    @Test
    void blockRoundTripsAndIsIdempotent() {
        String bob = id("bob");
        id("alice");

        // A repeated PUT must not fail on the primary key.
        given().auth().oauth2(token("alice")).when().put("/api/users/" + bob + "/block")
                .then().statusCode(204);
        given().auth().oauth2(token("alice")).when().put("/api/users/" + bob + "/block")
                .then().statusCode(204);

        given().auth().oauth2(token("alice"))
                .when().get("/api/me/blocked")
                .then().statusCode(200)
                .body("id", hasItem(bob));

        // A repeated DELETE is idempotent too, and clears the edge.
        unblock("alice", bob);
        unblock("alice", bob);

        given().auth().oauth2(token("alice"))
                .when().get("/api/me/blocked")
                .then().statusCode(200)
                .body("id", not(hasItem(bob)));
    }

    /** A user cannot block themselves: refused with 400, on both verbs. */
    @Test
    void blockingYourselfIsRejected() {
        String alice = id("alice");

        given().auth().oauth2(token("alice")).when().put("/api/users/" + alice + "/block")
                .then().statusCode(400);
        given().auth().oauth2(token("alice")).when().delete("/api/users/" + alice + "/block")
                .then().statusCode(400);
    }

    /** Blocking an id that is nobody is a 404, never a 403 that would confirm nothing. */
    @Test
    void blockingAnUnknownUserIsNotFound() {
        String unknown = UUID.randomUUID().toString();

        given().auth().oauth2(token("alice")).when().put("/api/users/" + unknown + "/block")
                .then().statusCode(404);
        given().auth().oauth2(token("alice")).when().delete("/api/users/" + unknown + "/block")
                .then().statusCode(404);
    }

    /**
     * Whether an account is blocked is visible only to the blocker. bob's block of carol shows
     * in bob's list and in neither alice's nor carol's — the blocked party is never told.
     */
    @Test
    void oneUsersBlockNeverLeaksIntoAnothersList() {
        id("alice");
        String carol = id("carol");

        given().auth().oauth2(token("bob")).when().put("/api/users/" + carol + "/block")
                .then().statusCode(204);

        // The blocker sees it.
        given().auth().oauth2(token("bob"))
                .when().get("/api/me/blocked")
                .then().statusCode(200)
                .body("id", hasItem(carol));

        // The blocked party is not told, and a third party sees nothing of it.
        given().auth().oauth2(token("carol"))
                .when().get("/api/me/blocked")
                .then().statusCode(200)
                .body("id", not(hasItem("bob")));
        given().auth().oauth2(token("alice"))
                .when().get("/api/me/blocked")
                .then().statusCode(200)
                .body("id", not(hasItem(carol)));

        // Leave the shared account state as it was found.
        unblock("bob", carol);
    }

    /**
     * A block overrides a follow (#203): once a block stands, a new follow attempt is refused
     * with 400 in <em>either</em> direction — the blocker's and the blocked party's alike.
     * Unblocking lifts the refusal.
     */
    @Test
    void aBlockRefusesAFollowInEitherDirection() {
        String alice = id("alice");
        String bob = id("bob");

        given().auth().oauth2(token("alice")).when().put("/api/users/" + bob + "/block")
                .then().statusCode(204);

        // The blocker cannot follow the blocked party.
        given().auth().oauth2(token("alice")).when().put("/api/users/" + bob + "/follow")
                .then().statusCode(400);
        // Nor can the blocked party follow the blocker: the block hides content both ways.
        given().auth().oauth2(token("bob")).when().put("/api/users/" + alice + "/follow")
                .then().statusCode(400);

        // Unblocking restores the ability to follow.
        unblock("alice", bob);
        given().auth().oauth2(token("alice")).when().put("/api/users/" + bob + "/follow")
                .then().statusCode(204);

        // Leave the shared account state as it was found.
        given().auth().oauth2(token("alice")).when().delete("/api/users/" + bob + "/follow")
                .then().statusCode(204);
    }
}
