package zelytra.librarius.web;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * #69: the push preferences and the devices that receive them. Everything is off until the
 * member switches it on, one category at a time, and a device belongs to whoever registered it
 * last.
 */
@QuarkusTest
class PushApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    @Inject
    EntityManager em;

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    private void prefs(String user, boolean releases, boolean wishlist, boolean goal) {
        given().auth().oauth2(token(user)).contentType("application/json")
                .body("{ \"releases\": %s, \"wishlist\": %s, \"goal\": %s }".formatted(releases, wishlist, goal))
                .when().put("/api/me/push").then().statusCode(200);
    }

    private void register(String user, String device, String platform, int expected) {
        given().auth().oauth2(token(user)).contentType("application/json")
                .body("{ \"token\": \"%s\", \"platform\": \"%s\" }".formatted(device, platform))
                .when().put("/api/me/push/devices").then().statusCode(expected);
    }

    @SuppressWarnings("unchecked")
    private List<String> owners(String device) {
        return QuarkusTransaction.requiringNew().call(() -> em
                .createNativeQuery("select user_id from push_device where token = :token")
                .setParameter("token", device).getResultList());
    }

    private String id(String user) {
        return given().auth().oauth2(token(user)).when().get("/api/me").then().extract().path("id");
    }

    @BeforeEach
    void allOff() {
        for (String user : List.of("alice", "bob")) {
            prefs(user, false, false, false);
        }
    }

    @Test
    void everythingIsOffUntilSwitchedOn() {
        given().auth().oauth2(token("carol")).when().get("/api/me/push").then().statusCode(200)
                .body("releases", is(false)).body("wishlist", is(false)).body("goal", is(false));
    }

    @Test
    void eachCategoryIsSwitchedOnItsOwn() {
        prefs("alice", true, false, true);
        given().auth().oauth2(token("alice")).when().get("/api/me/push").then().statusCode(200)
                .body("releases", is(true)).body("wishlist", is(false)).body("goal", is(true));
        given().auth().oauth2(token("bob")).when().get("/api/me/push").then()
                .body("releases", is(false));
    }

    @Test
    void refusesAnIncompleteChoiceAndAnonymousCalls() {
        given().auth().oauth2(token("alice")).contentType("application/json").body("{ \"releases\": true }")
                .when().put("/api/me/push").then().statusCode(400);
        given().when().get("/api/me/push").then().statusCode(401);
        given().contentType("application/json").body("{ \"token\": \"x\", \"platform\": \"android\" }")
                .when().put("/api/me/push/devices").then().statusCode(401);
    }

    @Test
    void aDeviceIsRegisteredOnceAndFollowsItsLastAccount() {
        String device = "fcm-" + UUID.randomUUID();
        register("alice", device, "android", 204);
        register("alice", device, "android", 204);
        assertEquals(List.of(id("alice")), owners(device));

        // The phone changed hands: it now receives bob's notifications, and only his.
        register("bob", device, "android", 204);
        assertEquals(List.of(id("bob")), owners(device));
    }

    @Test
    void refusesADeviceItCannotName() {
        register("alice", "", "android", 400);
        register("alice", "fcm-" + UUID.randomUUID(), "windows", 400);
    }

    @Test
    void aMemberForgetsOnlyTheirOwnDevice() {
        String device = "fcm-" + UUID.randomUUID();
        register("alice", device, "ios", 204);

        given().auth().oauth2(token("bob")).when().delete("/api/me/push/devices/" + device)
                .then().statusCode(404);
        assertEquals(List.of(id("alice")), owners(device));

        given().auth().oauth2(token("alice")).when().delete("/api/me/push/devices/" + device)
                .then().statusCode(204);
        assertEquals(List.of(), owners(device));
    }
}
