package zelytra.librarius.web;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.repository.AppUserRepository;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The editable profile (#75): {@code PATCH /api/me} over display name, interface language and
 * time zone.
 *
 * <p>The endpoint carries no identifier — a caller only ever edits their own {@code app_user},
 * so it cannot be pointed at anybody else. The isolation case is therefore not an id answering
 * 404 but the guarantee that one account's edit never reaches another's row, which
 * {@link #aProfileEditNeverReachesAnotherUsersRow()} pins down with two accounts.
 */
@QuarkusTest
class MeApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    @Inject
    AppUserRepository users;

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    /** Restores Bob to a known profile so the shared database cannot explain an assertion. */
    private void resetProfile(String user, String displayName, String locale, String timeZone) {
        String zone = timeZone == null ? "null" : "\"" + timeZone + "\"";
        given().auth().oauth2(token(user)).contentType("application/json")
                .body("{ \"displayName\": \"%s\", \"locale\": \"%s\", \"timeZone\": %s }"
                        .formatted(displayName, locale, zone))
                .when().patch("/api/me")
                .then().statusCode(200);
    }

    @Test
    void requiresAuthentication() {
        given().contentType("application/json")
                .body("{ \"displayName\": \"Anonyme\", \"locale\": \"fr\" }")
                .when().patch("/api/me")
                .then().statusCode(401);
    }

    /** The happy path: the three fields come back on the answer and on the next GET. */
    @Test
    void updatesTheThreeProfileFields() {
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("""
                        { "displayName": "Alice Liddell", "locale": "en",
                          "timeZone": "Europe/Paris" }
                        """)
                .when().patch("/api/me")
                .then().statusCode(200)
                .body("displayName", is("Alice Liddell"))
                .body("locale", is("en"))
                .body("timeZone", is("Europe/Paris"));

        given().auth().oauth2(token("alice"))
                .when().get("/api/me")
                .then().statusCode(200)
                .body("displayName", is("Alice Liddell"))
                .body("locale", is("en"))
                .body("timeZone", is("Europe/Paris"));

        // Leave the account where the rest of the suite expects to find it.
        resetProfile("alice", "alice", "fr", null);
    }

    /** A blank time zone clears the preference, back to the client's own zone. */
    @Test
    void aBlankTimeZoneClearsThePreference() {
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"displayName\": \"alice\", \"locale\": \"fr\", \"timeZone\": \"Asia/Tokyo\" }")
                .when().patch("/api/me")
                .then().statusCode(200)
                .body("timeZone", is("Asia/Tokyo"));

        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"displayName\": \"alice\", \"locale\": \"fr\", \"timeZone\": \"\" }")
                .when().patch("/api/me")
                .then().statusCode(200)
                .body("timeZone", is(nullValue()));

        resetProfile("alice", "alice", "fr", null);
    }

    /** An unknown time zone is a 400, like any other malformed input. */
    @Test
    void anUnknownTimeZoneIsRejected() {
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"displayName\": \"alice\", \"locale\": \"fr\", \"timeZone\": \"Middle/Earth\" }")
                .when().patch("/api/me")
                .then().statusCode(400);
    }

    /** A language the interface does not ship is refused. */
    @Test
    void anUnsupportedLocaleIsRejected() {
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"displayName\": \"alice\", \"locale\": \"de\", \"timeZone\": null }")
                .when().patch("/api/me")
                .then().statusCode(400);
    }

    /** A blank display name is refused: an account without a name to show is not editable. */
    @Test
    void aBlankDisplayNameIsRejected() {
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"displayName\": \"   \", \"locale\": \"fr\", \"timeZone\": null }")
                .when().patch("/api/me")
                .then().statusCode(400);
    }

    /**
     * The trust flag is server-computed and no endpoint accepts it (#180). {@code PATCH
     * /api/me} is the one writable {@code app_user} endpoint, so a request crafted to carry
     * {@code trusted} is where the guarantee is tested: the field is ignored and never reaches
     * the row, which stays untrusted whatever the body claims.
     */
    @Test
    void aCraftedRequestCannotFlipTheTrustFlag() {
        String id = given().auth().oauth2(token("alice")).contentType("application/json")
                .body("""
                        { "displayName": "alice", "locale": "fr", "timeZone": null,
                          "trusted": true, "trustedAt": "2000-01-01T00:00:00Z" }
                        """)
                .when().patch("/api/me")
                .then().statusCode(200)
                .extract().path("id");

        boolean trusted = QuarkusTransaction.requiringNew()
                .call(() -> users.findById(id).trusted);
        assertFalse(trusted, "a request body may not grant trust");

        resetProfile("alice", "alice", "fr", null);
    }

    /**
     * The isolation guarantee. The endpoint has no id to point at another user, so what has
     * to hold is that Alice editing her own profile leaves Bob's untouched. Bob is pinned to
     * a known state first, so the assertion on him cannot be something an earlier test left
     * behind in the shared database.
     */
    @Test
    void aProfileEditNeverReachesAnotherUsersRow() {
        resetProfile("bob", "bob", "fr", null);

        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("""
                        { "displayName": "Alice change tout", "locale": "en",
                          "timeZone": "America/New_York" }
                        """)
                .when().patch("/api/me")
                .then().statusCode(200);

        given().auth().oauth2(token("bob"))
                .when().get("/api/me")
                .then().statusCode(200)
                .body("id", notNullValue())
                .body("displayName", is("bob"))
                .body("locale", is("fr"))
                .body("timeZone", is(nullValue()));

        resetProfile("alice", "alice", "fr", null);
    }
}
