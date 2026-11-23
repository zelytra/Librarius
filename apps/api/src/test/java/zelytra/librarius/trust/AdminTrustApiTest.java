package zelytra.librarius.trust;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.domain.Work;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An admin sets a member's trusted standing by hand (#625), and the daily evaluation (#180,
 * #195) leaves that choice alone until the admin hands the member back to it. alice is the
 * admin; the members whose trust is changed are seeded fixtures, so no other test sees them move.
 */
@QuarkusTest
class AdminTrustApiTest {

    private static final KeycloakTestClient KEYCLOAK = new KeycloakTestClient();

    @Inject
    TrustEvaluator evaluator;

    @Inject
    EntityManager em;

    private static void setMode(String admin, String userId, String mode, int expected) {
        given().auth().oauth2(KEYCLOAK.getAccessToken(admin)).contentType("application/json")
                .body(Map.of("mode", mode))
                .when().put("/api/admin/users/" + userId + "/trust").then().statusCode(expected);
    }

    @Test
    void anAdminTrustsAMemberTheEvaluationWouldNot() {
        String member = seedUser(0, 0);
        evaluator.evaluateAll();
        assertFalse(trusted(member));

        setMode("alice", member, "TRUSTED", 204);
        assertTrue(trusted(member));

        // The next daily run keeps the admin's choice.
        evaluator.evaluateAll();
        assertTrue(trusted(member));
        given().auth().oauth2(KEYCLOAK.getAccessToken("bob")).when().get("/api/users/" + member + "/profile")
                .then().statusCode(200).body("trusted", is(true)).body("admin", is(false));
    }

    @Test
    void anAdminWithdrawsTrustTheEvaluationWouldGrant() {
        String member = seedUser(3, 2);
        evaluator.evaluateAll();
        assertTrue(trusted(member));

        setMode("alice", member, "UNTRUSTED", 204);
        assertFalse(trusted(member));
        evaluator.evaluateAll();
        assertFalse(trusted(member));
    }

    @Test
    void backToAutomaticTheEvaluationDecidesAgain() {
        String member = seedUser(0, 0);
        setMode("alice", member, "TRUSTED", 204);

        setMode("alice", member, "AUTO", 204);
        evaluator.evaluateAll();
        assertFalse(trusted(member));
    }

    @Test
    void theAdminListShowsEachMembersStandingAndMode() {
        String member = seedUser(0, 0);
        setMode("alice", member, "TRUSTED", 204);

        Map<String, Object> entry = given().auth().oauth2(KEYCLOAK.getAccessToken("alice"))
                .queryParam("size", 200).queryParam("q", "Trust fixture " + member.substring(6, 14))
                .when().get("/api/admin/users").then().statusCode(200).extract().jsonPath()
                .getMap("items.find { it.id == '" + member + "' }");
        assertEquals(true, entry.get("trusted"));
        assertEquals("TRUSTED", entry.get("trustMode"));
    }

    @Test
    void onlyAnAdminSetsTrust() {
        String member = seedUser(0, 0);
        setMode("bob", member, "TRUSTED", 403);
        setMode("alice", member, "NOPE", 400);
        setMode("alice", "nobody-" + UUID.randomUUID(), "TRUSTED", 404);
        assertFalse(trusted(member));
    }

    @Test
    void aProfileSaysWhetherTheMemberIsAnAdmin() {
        String alice = given().auth().oauth2(KEYCLOAK.getAccessToken("alice")).when().get("/api/me")
                .then().statusCode(200).extract().path("id");
        String bob = given().auth().oauth2(KEYCLOAK.getAccessToken("bob")).when().get("/api/me")
                .then().statusCode(200).extract().path("id");

        given().auth().oauth2(KEYCLOAK.getAccessToken("bob")).when().get("/api/users/" + alice + "/profile")
                .then().statusCode(200).body("admin", is(true));
        given().auth().oauth2(KEYCLOAK.getAccessToken("alice")).when().get("/api/users/" + bob + "/profile")
                .then().statusCode(200).body("admin", is(false));
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    /** An account with {@code readCount} titles read, backdated {@code ageMonths} months. */
    private String seedUser(int readCount, int ageMonths) {
        String userId = "trust-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            AppUser user = new AppUser();
            user.id = userId;
            user.displayName = "Trust fixture " + userId.substring(6, 14);
            em.persist(user);
            for (int i = 0; i < readCount; i++) {
                Work work = new Work();
                work.kind = Kind.BOOK;
                work.title = "Trust " + UUID.randomUUID();
                em.persist(work);
                Edition edition = new Edition();
                edition.work = work;
                em.persist(edition);
                LibraryItem item = new LibraryItem();
                item.userId = userId;
                item.edition = edition;
                item.status = LibraryStatus.READ;
                em.persist(item);
            }
        });
        if (ageMonths > 0) {
            OffsetDateTime backdated = OffsetDateTime.now().minusMonths(ageMonths).minusDays(1);
            QuarkusTransaction.requiringNew().run(() ->
                    em.createNativeQuery("update app_user set created_at = ?1 where id = ?2")
                            .setParameter(1, backdated).setParameter(2, userId).executeUpdate());
        }
        return userId;
    }

    private boolean trusted(String userId) {
        return QuarkusTransaction.requiringNew().call(() -> em.find(AppUser.class, userId).trusted);
    }
}
