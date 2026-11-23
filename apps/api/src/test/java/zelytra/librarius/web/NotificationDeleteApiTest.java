package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static zelytra.librarius.web.SocialTestSupport.relation;
import static zelytra.librarius.web.SocialTestSupport.strangers;
import static zelytra.librarius.web.SocialTestSupport.token;

/** #629: a member deletes their notifications, one by one or all at once — their own only. */
@QuarkusTest
class NotificationDeleteApiTest {

    @BeforeEach
    void reset() {
        strangers();
    }

    private static List<String> ids(String user) {
        return given().auth().oauth2(token(user)).queryParam("size", 200)
                .when().get("/api/notifications").then().statusCode(200).extract().jsonPath().getList("items.id");
    }

    /** A fresh FOLLOW notification for {@code recipient}, from {@code actor}; its id. */
    private static String notify(String actor, String recipient) {
        List<String> before = ids(recipient);
        relation(actor, recipient, "follow", true);
        return ids(recipient).stream().filter(id -> !before.contains(id)).findFirst().orElseThrow();
    }

    @Test
    void aMemberDeletesOneOfTheirNotifications() {
        String fromCarol = notify("carol", "bob");
        String fromAlice = notify("alice", "bob");

        given().auth().oauth2(token("bob")).when().delete("/api/notifications/" + fromCarol).then().statusCode(204);

        assertFalse(ids("bob").contains(fromCarol));
        assertTrue(ids("bob").contains(fromAlice));
    }

    @Test
    void aMemberClearsAllTheirNotificationsAndNobodyElses() {
        notify("carol", "bob");
        String alices = notify("carol", "alice");

        given().auth().oauth2(token("bob")).when().delete("/api/notifications").then().statusCode(204);

        assertEquals(List.of(), ids("bob"));
        assertEquals(0, given().auth().oauth2(token("bob")).when().get("/api/notifications/unread")
                .then().statusCode(200).extract().<Integer>path("count"));
        assertTrue(ids("alice").contains(alices));
    }

    @Test
    void anotherMembersNotificationCannotBeDeleted() {
        String alices = notify("carol", "alice");

        given().auth().oauth2(token("bob")).when().delete("/api/notifications/" + alices).then().statusCode(404);
        given().auth().oauth2(token("bob")).when().delete("/api/notifications/" + UUID.randomUUID()).then().statusCode(404);
        assertTrue(ids("alice").contains(alices));
    }
}
