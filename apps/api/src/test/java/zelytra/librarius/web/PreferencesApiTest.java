package zelytra.librarius.web;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.UserPreferences;
import zelytra.librarius.domain.repository.AppUserRepository;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * #613: the display preferences follow the account, not the device — {@code PATCH
 * /api/me/preferences} merges what it is given, and {@code GET /api/me} hands the result to
 * every device the member signs in on.
 *
 * <p>Like the rest of {@code /api/me}, the endpoint carries no identifier: the isolation case
 * is that one account's choice never shows on another's profile.
 */
@QuarkusTest
class PreferencesApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    @Inject
    AppUserRepository users;

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    private io.restassured.response.ValidatableResponse patch(String user, String body) {
        return given().auth().oauth2(token(user)).contentType("application/json").body(body)
                .when().patch("/api/me/preferences").then();
    }

    /** Back to "nothing chosen", so the shared database explains no assertion. */
    @BeforeEach
    void clear() {
        for (String user : new String[] {"alice", "bob"}) {
            // Provisions the account on its first call, then forgets whatever it chose.
            given().auth().oauth2(token(user)).when().get("/api/me").then().statusCode(200);
        }
        QuarkusTransaction.requiringNew().run(() -> users.listAll()
                .forEach(u -> u.preferences = UserPreferences.NONE));
    }

    @Test
    void requiresAuthentication() {
        given().contentType("application/json").body("{ \"theme\": \"rose\" }")
                .when().patch("/api/me/preferences").then().statusCode(401);
    }

    @Test
    void aFreshAccountHasChosenNothing() {
        given().auth().oauth2(token("alice")).when().get("/api/me").then().statusCode(200)
                .body("preferences.theme", nullValue())
                .body("preferences.bottomBar", nullValue())
                .body("preferences.libraryKind", nullValue())
                .body("preferences.sheetTheme", nullValue());
    }

    @Test
    void storesEveryPreferenceAndReturnsItOnTheProfile() {
        patch("alice", """
                { "theme": "rose", "bottomBar": ["/", "/collection", "/people"],
                  "libraryKind": "MANGA", "sheetTheme": "notebook" }
                """).statusCode(200)
                .body("theme", is("rose"))
                .body("bottomBar", contains("/", "/collection", "/people"));

        given().auth().oauth2(token("alice")).when().get("/api/me").then().statusCode(200)
                .body("preferences.theme", is("rose"))
                .body("preferences.bottomBar", contains("/", "/collection", "/people"))
                .body("preferences.libraryKind", is("MANGA"))
                .body("preferences.sheetTheme", is("notebook"));
    }

    /** Two devices changing two different settings must not undo each other. */
    @Test
    void anAbsentFieldKeepsWhatWasStored() {
        patch("alice", "{ \"theme\": \"ocean\" }").statusCode(200);
        patch("alice", "{ \"sheetTheme\": \"classic\" }").statusCode(200)
                .body("theme", is("ocean"))
                .body("sheetTheme", is("classic"));
    }

    @Test
    void autoIsAChoiceOfLibraryKind() {
        patch("alice", "{ \"libraryKind\": \"BOOK\" }").statusCode(200);
        patch("alice", "{ \"libraryKind\": \"auto\" }").statusCode(200)
                .body("libraryKind", is("auto"));
    }

    @Test
    void refusesAValueOutsideTheKnownSets() {
        patch("alice", "{ \"libraryKind\": \"COOKBOOK\" }").statusCode(400);
        patch("alice", "{ \"sheetTheme\": \"neon\" }").statusCode(400);
        patch("alice", "{ \"theme\": \"<script>\" }").statusCode(400);
    }

    @Test
    void refusesABottomBarItCouldNotDraw() {
        patch("alice", "{ \"bottomBar\": [] }").statusCode(400);
        patch("alice", "{ \"bottomBar\": [\"/\", \"/a\", \"/b\", \"/c\", \"/d\"] }").statusCode(400);
        patch("alice", "{ \"bottomBar\": [\"https://evil.example\"] }").statusCode(400);
    }

    @Test
    void oneAccountsChoiceNeverReachesAnother() {
        patch("alice", "{ \"theme\": \"lavande\" }").statusCode(200);
        given().auth().oauth2(token("bob")).when().get("/api/me").then().statusCode(200)
                .body("preferences.theme", nullValue());
    }
}
