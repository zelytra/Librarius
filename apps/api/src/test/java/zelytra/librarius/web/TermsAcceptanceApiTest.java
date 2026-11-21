package zelytra.librarius.web;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.domain.repository.AppUserRepository;
import zelytra.librarius.legal.LegalTerms;

import java.time.OffsetDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #74: a member accepts the terms of service and the privacy policy once, on their first
 * sign-in, and again whenever their version changes. The acceptance is recorded server-side
 * with the version accepted and the server's own clock, never a date the client sends.
 */
@QuarkusTest
class TermsAcceptanceApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    @Inject
    AppUserRepository users;

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    private void accept(String user, int version, int status) {
        given().auth().oauth2(token(user)).contentType("application/json")
                .body("{ \"version\": " + version + " }")
                .when().put("/api/me/terms")
                .then().statusCode(status);
    }

    /** Both accounts start without an acceptance, whatever an earlier test recorded. */
    @BeforeEach
    void clearAcceptances() {
        // Provisions the rows if this is the accounts' first call.
        given().auth().oauth2(token("alice")).when().get("/api/me").then().statusCode(200);
        given().auth().oauth2(token("bob")).when().get("/api/me").then().statusCode(200);
        QuarkusTransaction.requiringNew().run(() -> users.update("termsVersion = null, termsAcceptedAt = null"));
    }

    @Test
    void aNewMemberHasAcceptedNothingYet() {
        given().auth().oauth2(token("alice")).when().get("/api/me")
                .then().statusCode(200)
                .body("termsVersion", nullValue())
                .body("termsAcceptedAt", nullValue())
                .body("currentTermsVersion", is(LegalTerms.CURRENT_VERSION));
    }

    @Test
    void acceptingRecordsTheVersionAndTheServersTime() {
        OffsetDateTime before = OffsetDateTime.now().minusSeconds(1);

        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"version\": " + LegalTerms.CURRENT_VERSION + " }")
                .when().put("/api/me/terms")
                .then().statusCode(200)
                .body("termsVersion", is(LegalTerms.CURRENT_VERSION))
                .body("termsAcceptedAt", notNullValue());

        given().auth().oauth2(token("alice")).when().get("/api/me")
                .then().statusCode(200).body("termsVersion", is(LegalTerms.CURRENT_VERSION));
        AppUser alice = QuarkusTransaction.requiringNew().call(() -> users.find("username", "alice").firstResult());
        assertEquals(LegalTerms.CURRENT_VERSION, alice.termsVersion);
        assertNotNull(alice.termsAcceptedAt);
        assertTrue(alice.termsAcceptedAt.isAfter(before));
    }

    /** Accepting a version the app does not show — an old one, a future one — is refused. */
    @Test
    void refusesAVersionThatIsNotTheCurrentOne() {
        accept("alice", LegalTerms.CURRENT_VERSION + 1, 400);
        accept("alice", 0, 400);
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{}").when().put("/api/me/terms").then().statusCode(400);
        given().auth().oauth2(token("alice")).when().get("/api/me")
                .then().statusCode(200).body("termsVersion", nullValue());
    }

    @Test
    void requiresAuthentication() {
        given().contentType("application/json")
                .body("{ \"version\": " + LegalTerms.CURRENT_VERSION + " }")
                .when().put("/api/me/terms")
                .then().statusCode(401);
    }

    /** One member's acceptance never stands for another's. */
    @Test
    void anAcceptanceNeverReachesAnotherMember() {
        accept("alice", LegalTerms.CURRENT_VERSION, 200);

        given().auth().oauth2(token("bob")).when().get("/api/me")
                .then().statusCode(200).body("termsVersion", nullValue());
        AppUser bob = QuarkusTransaction.requiringNew().call(() -> users.find("username", "bob").firstResult());
        assertNull(bob.termsAcceptedAt);
    }
}
