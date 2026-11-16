package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static zelytra.librarius.web.SocialTestSupport.add;
import static zelytra.librarius.web.SocialTestSupport.id;
import static zelytra.librarius.web.SocialTestSupport.mutual;
import static zelytra.librarius.web.SocialTestSupport.relation;
import static zelytra.librarius.web.SocialTestSupport.strangers;
import static zelytra.librarius.web.SocialTestSupport.token;

/** #516, #211: the bell — followed readers' activity, and reactions and comments on mine. */
@QuarkusTest
class NotificationApiTest {

    private static JsonPath notifications(String user) {
        return given().auth().oauth2(token(user)).queryParam("size", 100).when().get("/api/notifications")
                .then().statusCode(200).extract().jsonPath();
    }

    /** The notifications of {@code user} about {@code title}. */
    private static List<Map<String, Object>> about(String user, String title) {
        return notifications(user).getList("items.findAll { it.book?.title == '" + title + "' }");
    }

    private static int unread(String user) {
        return given().auth().oauth2(token(user)).when().get("/api/notifications/unread")
                .then().statusCode(200).extract().path("count");
    }

    private static void notify(String follower, String member, boolean on, int expected) {
        given().auth().oauth2(token(follower)).contentType("application/json").body(Map.of("notifyActivity", on))
                .when().put("/api/users/" + id(member) + "/follow/notify").then().statusCode(expected);
    }

    private static String eventOf(String viewer, String title) {
        return given().auth().oauth2(token(viewer)).queryParam("size", 100).when().get("/api/feed")
                .then().extract().path("items.find { it.book.title == '" + title + "' }.id");
    }

    @BeforeEach
    void clean() {
        strangers();
        for (String user : List.of("alice", "bob", "carol")) {
            given().auth().oauth2(token(user)).when().post("/api/notifications/read").then().statusCode(204);
        }
    }

    @Test
    void aFollowedReadersActivityNotifiesByDefaultAndOpensTheEvent() {
        mutual("alice", "bob");
        String title = "Notifié " + UUID.randomUUID();
        add("bob", title, "READ");

        List<Map<String, Object>> mine = about("alice", title);
        assertEquals(1, mine.size());
        assertEquals("ACTIVITY", mine.get(0).get("type"));
        assertEquals("FINISHED_READING", mine.get(0).get("eventType"));
        assertNotNull(mine.get(0).get("eventId"));
        assertTrue(unread("alice") >= 1);

        // The screen a notification opens: the event itself, with its reactions and comments.
        given().auth().oauth2(token("alice")).when().get("/api/feed/" + mine.get(0).get("eventId"))
                .then().statusCode(200).body("book.title", org.hamcrest.Matchers.is(title));

        given().auth().oauth2(token("alice")).when().post("/api/notifications/read").then().statusCode(204);
        assertEquals(0, unread("alice"));
        assertNotNull(about("alice", title).get(0).get("readAt"));
    }

    @Test
    void theNotificationsOfAFollowAreSwitchedOffOnTheProfile() {
        mutual("alice", "bob");
        notify("alice", "bob", false, 204);
        assertEquals(Boolean.FALSE, given().auth().oauth2(token("alice")).when()
                .get("/api/users/" + id("bob") + "/profile").then().extract().path("notifyActivity"));

        String title = "Silencieux " + UUID.randomUUID();
        add("bob", title, "READ");
        assertEquals(0, about("alice", title).size());

        notify("alice", "bob", true, 204);
        String next = "De nouveau " + UUID.randomUUID();
        add("bob", next, "READ");
        assertEquals(1, about("alice", next).size());
        // Only a member one follows.
        notify("carol", "bob", true, 404);
    }

    @Test
    void nothingReachesAReaderWhoMayNotSeeTheAuthor() {
        relation("carol", "bob", "follow", true); // one way: bob's private account stays out of reach
        String title = "Privé " + UUID.randomUUID();
        add("bob", title, "READ");
        assertEquals(0, about("carol", title).size());
        assertEquals(0, about("bob", title).size(), "nobody is notified of their own activity");
    }

    /** #211: a comment or a reaction on my activity reaches me, never my own. */
    @Test
    void aCommentAndAReactionOnMyActivityReachMe() {
        mutual("alice", "bob");
        String title = "Commenté " + UUID.randomUUID();
        add("bob", title, "READ");
        String event = eventOf("alice", title);

        given().auth().oauth2(token("alice")).contentType("application/json").body(Map.of("comment", "Super !"))
                .when().post("/api/feed/" + event + "/comments").then().statusCode(201);
        given().auth().oauth2(token("alice")).contentType("application/json").body(Map.of("emote", "LOVE"))
                .when().put("/api/feed/" + event + "/reaction").then().statusCode(204);
        given().auth().oauth2(token("bob")).contentType("application/json").body(Map.of("comment", "Merci"))
                .when().post("/api/feed/" + event + "/comments").then().statusCode(201);

        List<Map<String, Object>> bobs = about("bob", title);
        assertEquals(List.of("REACTION", "COMMENT"),
                bobs.stream().map(n -> (String) n.get("type")).toList(), "newest first, none for bob's own comment");
        assertEquals(id("alice"), ((Map<?, ?>) bobs.get(1).get("actor")).get("id"));
        assertEquals("Super !", bobs.get(1).get("comment"));
    }

    @Test
    void aReaderWhoTurnedCommentsOffIsNotToldOfThem() {
        mutual("alice", "bob");
        setNotifyComments("bob", false);
        try {
            String title = "Sans commentaires " + UUID.randomUUID();
            add("bob", title, "READ");
            given().auth().oauth2(token("alice")).contentType("application/json").body(Map.of("comment", "Coucou"))
                    .when().post("/api/feed/" + eventOf("alice", title) + "/comments").then().statusCode(201);
            assertEquals(0, about("bob", title).size());
        } finally {
            setNotifyComments("bob", true);
        }
    }

    @Test
    void anEventOutOfReachIsNotOpenedAndNotificationsAreNeverAnotherReaders() {
        String title = "Hors de portée " + UUID.randomUUID();
        add("bob", title, "READ");
        String event = eventOf("bob", title);
        given().auth().oauth2(token("carol")).when().get("/api/feed/" + event).then().statusCode(404);
        given().auth().oauth2(token("carol")).when().get("/api/feed/" + UUID.randomUUID()).then().statusCode(404);
        given().when().get("/api/notifications").then().statusCode(401);
        assertNull(notifications("carol").get("items.find { it.book?.title == '" + title + "' }"));
    }

    private static void setNotifyComments(String user, boolean on) {
        JsonPath me = given().auth().oauth2(token(user)).when().get("/api/me").then().extract().jsonPath();
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("displayName", me.getString("displayName"));
        body.put("locale", me.getString("locale"));
        body.put("timeZone", me.getString("timeZone"));
        body.put("publicAccount", me.getBoolean("publicAccount"));
        body.put("notifyComments", on);
        given().auth().oauth2(token(user)).contentType("application/json").body(body)
                .when().patch("/api/me").then().statusCode(200);
        assertEquals(on, given().auth().oauth2(token(user)).when().get("/api/me").then().extract().path("notifyComments"));
    }
}
