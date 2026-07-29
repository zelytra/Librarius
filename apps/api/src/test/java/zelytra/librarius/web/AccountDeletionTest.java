package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zelytra.librarius.account.DatabaseProbe;
import zelytra.librarius.account.KeycloakAccountDeleter.Outcome;
import zelytra.librarius.account.RecordingKeycloakAccountDeleter;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Right to erasure (GDPR art. 17): {@code DELETE /api/me}.
 *
 * <p>Runs as <b>carol</b>, a third Dev Services account that exists for this test alone. The
 * suite shares one database, and this is the only test that empties one — doing it as alice
 * or bob would pull the ground out from under whatever ran next.
 *
 * <p>Each test starts by deleting the account, so the class is order-independent and every
 * method begins from an account that does not exist.
 */
@QuarkusTest
class AccountDeletionTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    @Inject
    DatabaseProbe probe;

    @Inject
    RecordingKeycloakAccountDeleter identityProvider;

    private String token() {
        return keycloak.getAccessToken("carol");
    }

    private String subject() {
        return given().auth().oauth2(token()).when().get("/api/me")
                .then().statusCode(200)
                .extract().path("id");
    }

    @BeforeEach
    void cleanSlate() {
        identityProvider.reset();
        given().auth().oauth2(token()).when().delete("/api/me").then().statusCode(200);
    }

    /** Fills the account with one of everything a user can own. */
    private void fillAccount() {
        given().auth().oauth2(token()).contentType("application/json")
                .body("""
                        { "book": { "kind": "MANGA", "title": "Erasure vol. 1",
                                    "authors": "Deletion Test", "seriesTitle": "Erasure",
                                    "volumeNumber": 1, "genres": "Seinen" },
                          "status": "READING", "rating": 4, "acquiredAt": "2026-01-05" }
                        """)
                .when().post("/api/library").then().statusCode(201);

        String itemId = given().auth().oauth2(token()).when().get("/api/library")
                .then().statusCode(200).extract().path("items[0].id");

        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"currentPage\": 42, \"percent\": 20, \"status\": \"READING\" }")
                .when().put("/api/library/" + itemId + "/progress").then().statusCode(204);

        String category = given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"label\": \"Erasure favourites\", \"color\": \"#123456\" }")
                .when().post("/api/categories").then().statusCode(200).extract().path("id");

        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"categoryId\": \"" + category + "\" }")
                .when().put("/api/library/" + itemId + "/rank").then().statusCode(200);

        given().auth().oauth2(token()).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "Erasure wish",
                                    "authors": "Deletion Test" },
                          "priority": "PRIORITY", "estimatedPrice": 19.90, "note": "à offrir" }
                        """)
                .when().post("/api/wishlist").then().statusCode(201);

        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"targetCount\": 30, \"unit\": \"BOOKS\" }")
                .when().put("/api/goals/2990").then().statusCode(200);

        given().auth().oauth2(token()).when().put("/api/series/" + seriesId() + "/follow")
                .then().statusCode(204);

        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"sections\": [ { \"code\": \"goal\", \"hidden\": true } ] }")
                .when().put("/api/dashboard/layout").then().statusCode(200);
    }

    private String seriesId() {
        return given().auth().oauth2(token()).when().get("/api/series")
                .then().statusCode(200)
                .extract().path("find { it.title == 'Erasure' }.id");
    }

    // ── Erasure ───────────────────────────────────────────────────────────────

    /**
     * The acceptance criterion of #73, asserted on the tables rather than on the API: after
     * the deletion no row anywhere references the user.
     *
     * <p>The probe finds the tables to check in {@code information_schema}, so a user-scoped
     * table added later without a cascade fails here the day it is created.
     */
    @Test
    void deletingAnAccountLeavesNoRowBehind() {
        String userId = subject();
        fillAccount();

        // Everything is really there before the deletion — otherwise "no rows left" would
        // be satisfied by an account that was always empty.
        Map<String, Long> before = probe.personalRows(userId);
        assertEquals(1L, before.get("app_user"));
        assertEquals(1L, before.get("library_item"));
        assertEquals(1L, before.get("wishlist_item"));
        assertEquals(1L, before.get("reading_goal"));
        assertEquals(1L, before.get("rank_category"));
        assertEquals(1L, before.get("series_follow"));
        assertEquals(1L, before.get("reading_progress"));
        assertEquals(1L, before.get("dashboard_layout"));

        given().auth().oauth2(token())
                .when().delete("/api/me")
                .then().statusCode(200)
                .body("libraryItems", is(1))
                .body("wishlistItems", is(1))
                .body("goals", is(1))
                .body("categories", is(1))
                .body("seriesFollows", is(1));

        probe.personalRows(userId).forEach((table, rows) ->
                assertEquals(0L, rows, "rows left in " + table + " for the deleted account"));
    }

    /** The Keycloak account goes too — that is what makes signing back in impossible. */
    @Test
    void theIdentityProviderIsAskedToDeleteTheSameSubject() {
        String userId = subject();

        given().auth().oauth2(token()).when().delete("/api/me").then().statusCode(200);

        assertTrue(identityProvider.deletedSubjects().contains(userId),
                "the Keycloak account must be deleted along with the data");
    }

    /**
     * The shared catalog is not personal data: the work, the edition and the series a user
     * entered stay behind, because every other collection is built on the same rows.
     */
    @Test
    void theSharedCatalogSurvivesTheDeletion() {
        fillAccount();

        UUID editionId = UUID.fromString(given().auth().oauth2(token())
                .when().get("/api/library").then().statusCode(200)
                .extract().path("items[0].book.editionId"));
        UUID seriesId = UUID.fromString(seriesId());
        UUID workId = UUID.fromString(given().auth().oauth2(token())
                .when().get("/api/series/" + seriesId).then().statusCode(200)
                .extract().path("volumes.find { it.workId != null }.workId"));

        given().auth().oauth2(token()).when().delete("/api/me").then().statusCode(200);

        assertTrue(probe.catalogRowExists("edition", editionId), "edition must survive");
        assertTrue(probe.catalogRowExists("work", workId), "work must survive");
        assertTrue(probe.catalogRowExists("series", seriesId), "series must survive");
    }

    /** Deleting an account that owns nothing is not an error — it is the common case. */
    @Test
    void deletingAnEmptyAccountSucceeds() {
        given().auth().oauth2(token())
                .when().delete("/api/me")
                .then().statusCode(200)
                .body("libraryItems", is(0));
    }

    // ── Degradation ───────────────────────────────────────────────────────────

    /**
     * The rule that decides the ordering: if the identity provider does not delete the
     * login, the data stays. The opposite would hand the user a fresh, empty account on
     * their next sign-in, which looks exactly like having lost everything.
     */
    @Test
    void nothingIsErasedWhenTheIdentityProviderFails() {
        String userId = subject();
        fillAccount();
        identityProvider.willAnswer(Outcome.FAILED);

        given().auth().oauth2(token())
                .when().delete("/api/me")
                .then().statusCode(503)
                .body("message", containsString("Rien n'a été supprimé"));

        Map<String, Long> after = probe.personalRows(userId);
        assertEquals(1L, after.get("app_user"));
        assertEquals(1L, after.get("library_item"), "the collection must be untouched");
        assertEquals(1L, after.get("wishlist_item"), "the wishlist must be untouched");
    }

    /** An instance with no service account configured cannot delete accounts at all. */
    @Test
    void nothingIsErasedWhenNoServiceAccountIsConfigured() {
        String userId = subject();
        fillAccount();
        identityProvider.willAnswer(Outcome.NOT_CONFIGURED);

        given().auth().oauth2(token())
                .when().delete("/api/me")
                .then().statusCode(503)
                .body("message", containsString("n'ont pas été touchées"));

        assertEquals(1L, probe.personalRows(userId).get("library_item"));
    }

    /**
     * A retry after a half-finished deletion has to be able to finish: Keycloak answering
     * "no such user" means the login is already gone, not that the erasure must stop.
     */
    @Test
    void anAlreadyDeletedLoginStillErasesTheData() {
        String userId = subject();
        fillAccount();
        identityProvider.willAnswer(Outcome.ALREADY_ABSENT);

        given().auth().oauth2(token()).when().delete("/api/me").then().statusCode(200);

        assertEquals(0L, probe.personalRows(userId).get("app_user"));
    }

    // ── Isolation ─────────────────────────────────────────────────────────────

    @Test
    void deletionRequiresAToken() {
        given().when().delete("/api/me").then().statusCode(401);
    }
}
