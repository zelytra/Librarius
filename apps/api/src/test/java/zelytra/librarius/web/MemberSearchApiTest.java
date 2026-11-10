package zelytra.librarius.web;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.AppUser;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** #202: finding people to follow, by name. */
@QuarkusTest
class MemberSearchApiTest {

    private static final KeycloakTestClient KEYCLOAK = new KeycloakTestClient();

    @Inject
    EntityManager em;

    private static String token() {
        return KEYCLOAK.getAccessToken("alice");
    }

    private String member(String name) {
        String id = "member-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            AppUser u = new AppUser();
            u.id = id;
            u.displayName = name;
            em.persist(u);
        });
        return id;
    }

    private List<String> search(String q) {
        return given().auth().oauth2(token()).queryParam("q", q)
                .when().get("/api/users").then().statusCode(200).extract().jsonPath().getList("displayName");
    }

    @Test
    void findsMembersByNameAndSaysWhomTheCallerFollows() {
        String marker = "Zéphyrin" + UUID.randomUUID().toString().substring(0, 6);
        String followed = member(marker + " Premier");
        member(marker + " Second");

        given().auth().oauth2(token()).when().put("/api/users/" + followed + "/follow").then().statusCode(204);

        List<String> names = search(marker.toLowerCase());
        assertEquals(List.of(marker + " Premier", marker + " Second"), names);
        List<Boolean> following = given().auth().oauth2(token()).queryParam("q", marker)
                .when().get("/api/users").then().extract().jsonPath().getList("following");
        assertEquals(List.of(true, false), following);
    }

    @Test
    void leavesOutABlockedMemberAndAnswersNothingForOneLetter() {
        String marker = "Blockable" + UUID.randomUUID().toString().substring(0, 6);
        String blocked = member(marker + " Bloqué");

        given().auth().oauth2(token()).when().put("/api/users/" + blocked + "/block").then().statusCode(204);

        assertTrue(search(marker).isEmpty());
        assertTrue(search("z").isEmpty());
    }
}
