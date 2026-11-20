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
import static zelytra.librarius.web.SocialTestSupport.mutual;
import static zelytra.librarius.web.SocialTestSupport.publicAccount;
import static zelytra.librarius.web.SocialTestSupport.relation;
import static zelytra.librarius.web.SocialTestSupport.shareReview;
import static zelytra.librarius.web.SocialTestSupport.strangers;
import static zelytra.librarius.web.SocialTestSupport.token;

/** #209, #210: the feed of what followed members did, with reactions and comments. */
@QuarkusTest
class FeedApiTest {

    private static JsonPath feed(String viewer) {
        return given().auth().oauth2(token(viewer)).queryParam("size", 100)
                .when().get("/api/feed").then().statusCode(200).extract().jsonPath();
    }

    /** The viewer's feed events about {@code title}. */
    private static List<Map<String, Object>> about(String viewer, String title) {
        return feed(viewer).getList("items.findAll { it.book.title == '" + title + "' }");
    }

    private static String eventId(String viewer, String title, String type) {
        return feed(viewer).getString("items.find { it.book.title == '" + title + "' && it.type == '" + type + "' }.id");
    }

    @BeforeEach
    void noRelationship() {
        strangers();
    }

    @Test
    void finishingWishingAndSharingAReviewEachMakeOneEvent() {
        mutual("alice", "bob");
        String read = "Fini " + UUID.randomUUID();
        String item = add("bob", read, "READ").getString("id");
        shareReview("bob", item, 4, "Formidable");
        shareReview("bob", item, 4.5, "Formidable, relu"); // already shared: no second event

        String wished = "Souhait " + UUID.randomUUID();
        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("{ \"book\": { \"kind\": \"BOOK\", \"title\": \"" + wished + "\" } }")
                .when().post("/api/wishlist").then().statusCode(201);

        List<Map<String, Object>> events = about("alice", read);
        assertEquals(2, events.size(), "one FINISHED_READING and one REVIEW_SHARED: " + events);
        assertNotNull(eventId("alice", read, "FINISHED_READING"));
        assertEquals("Formidable, relu",
                feed("alice").getString("items.find { it.type == 'REVIEW_SHARED' && it.book.title == '" + read + "' }.review"));
        assertEquals(1, about("alice", wished).size());
        assertEquals("WISHLIST_ADDED", about("alice", wished).get(0).get("type"));
    }

    @Test
    void finishingABookBeingReadMakesAnEvent() {
        mutual("alice", "bob");
        String title = "Lecture " + UUID.randomUUID();
        String item = add("bob", title, "READING").getString("id");
        assertEquals(0, about("alice", title).size());

        given().auth().oauth2(token("bob")).contentType("application/json").body("{ \"status\": \"READ\" }")
                .when().put("/api/library/" + item + "/progress").then().statusCode(204);
        assertEquals(1, about("alice", title).size());
    }

    /** #569: a finished book says how long it was read, when the reading has a start date. */
    @Test
    void aFinishedBookCarriesItsReadingTime() {
        mutual("alice", "bob");
        String timed = "Chronométré " + UUID.randomUUID();
        String item = add("bob", timed, "TO_READ").getString("id");
        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("{ \"status\": \"READING\", \"startedAt\": \"2026-01-01\" }")
                .when().put("/api/library/" + item + "/progress").then().statusCode(204);
        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("{ \"status\": \"READ\", \"startedAt\": \"2026-01-01\", \"finishedAt\": \"2026-01-10\" }")
                .when().put("/api/library/" + item + "/progress").then().statusCode(204);

        Map<String, Object> event = about("alice", timed).get(0);
        assertEquals(10, event.get("readingDays"));

        // Added straight as read, a book has no reading time to tell.
        String instant = "Sans durée " + UUID.randomUUID();
        add("bob", instant, "READ");
        Map<String, Object> bare = about("alice", instant).stream()
                .filter(e -> "FINISHED_READING".equals(e.get("type"))).findFirst().orElseThrow();
        assertNull(bare.get("readingDays"));
    }

    @Test
    void onlyWhoMayBeSeenReachesTheFeed() {
        String title = "Isolé " + UUID.randomUUID();
        add("bob", title, "READ");

        relation("carol", "bob", "follow", true);
        assertEquals(0, about("carol", title).size(), "a one-way follow contributes nothing");
        assertEquals(1, about("bob", title).size(), "a reader's own events are in their feed (#511)");

        publicAccount("bob", true);
        assertEquals(1, about("carol", title).size(), "a followed public account is seen");

        relation("bob", "carol", "block", true);
        assertEquals(0, about("carol", title).size(), "a block hides it");
    }

    @Test
    void aReactionIsOnePerMemberAndChangingItReplacesIt() {
        mutual("alice", "bob");
        mutual("carol", "bob");
        String title = "Réaction " + UUID.randomUUID();
        add("bob", title, "READ");
        String event = eventId("alice", title, "FINISHED_READING");

        react("alice", event, "LOVE", 204);
        react("alice", event, "CLAP", 204);
        react("carol", event, "CLAP", 204);
        JsonPath seen = feed("alice");
        String path = "items.find { it.id == '" + event + "' }";
        assertEquals(2, seen.getInt(path + ".reactions.CLAP"));
        assertNull(seen.get(path + ".reactions.LOVE"), "the first reaction was replaced");
        assertEquals("CLAP", seen.getString(path + ".myReaction"));

        given().auth().oauth2(token("alice")).when().delete("/api/feed/" + event + "/reaction").then().statusCode(204);
        assertEquals(1, feed("alice").getInt(path + ".reactions.CLAP"));
        assertNull(feed("alice").get(path + ".myReaction"));
        react("alice", event, "NOPE", 400);
    }

    @Test
    void aCommentIsFlatAndOnlyItsAuthorDeletesIt() {
        mutual("alice", "bob");
        mutual("carol", "bob");
        String title = "Commentaire " + UUID.randomUUID();
        add("bob", title, "READ");
        String event = eventId("alice", title, "FINISHED_READING");

        String comment = given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"comment\": \"Je l'ai adoré aussi\" }")
                .when().post("/api/feed/" + event + "/comments").then().statusCode(201).extract().path("id");
        given().auth().oauth2(token("alice")).contentType("application/json").body("{ \"comment\": \"  \" }")
                .when().post("/api/feed/" + event + "/comments").then().statusCode(400);

        String path = "items.find { it.id == '" + event + "' }.comments";
        assertEquals("Je l'ai adoré aussi", feed("carol").getString(path + "[0].comment"));
        assertEquals(Boolean.FALSE, feed("carol").get(path + "[0].mine"));

        given().auth().oauth2(token("carol")).when().delete("/api/feed/" + event + "/comments/" + comment)
                .then().statusCode(404);
        given().auth().oauth2(token("alice")).when().delete("/api/feed/" + event + "/comments/" + comment)
                .then().statusCode(204);
        assertEquals(0, feed("carol").getList(path).size());
    }

    /** Seeing an event and acting on it are one permission: an unfollow takes both away. */
    @Test
    void anEventThatStoppedBeingVisibleCannotBeReactedToNorCommented() {
        mutual("alice", "bob");
        String title = "Plus visible " + UUID.randomUUID();
        add("bob", title, "READ");
        String event = eventId("alice", title, "FINISHED_READING");

        relation("bob", "alice", "follow", false);
        react("alice", event, "LOVE", 404);
        given().auth().oauth2(token("alice")).contentType("application/json").body("{ \"comment\": \"Coucou\" }")
                .when().post("/api/feed/" + event + "/comments").then().statusCode(404);
        react("alice", UUID.randomUUID().toString(), "LOVE", 404);
    }

    @Test
    void aCommentFromSomeoneTheViewerBlocksIsHidden() {
        mutual("alice", "bob");
        mutual("carol", "bob");
        String title = "Bloqué " + UUID.randomUUID();
        add("bob", title, "READ");
        String event = eventId("alice", title, "FINISHED_READING");
        given().auth().oauth2(token("carol")).contentType("application/json").body("{ \"comment\": \"De carol\" }")
                .when().post("/api/feed/" + event + "/comments").then().statusCode(201);

        relation("alice", "carol", "block", true);
        assertTrue(feed("alice").getList("items.find { it.id == '" + event + "' }.comments").isEmpty());
    }

    @Test
    void theFeedNeedsASession() {
        given().when().get("/api/feed").then().statusCode(401);
    }

    private static void react(String user, String event, String emote, int expected) {
        given().auth().oauth2(token(user)).contentType("application/json").body("{ \"emote\": \"" + emote + "\" }")
                .when().put("/api/feed/" + event + "/reaction").then().statusCode(expected);
    }

    /** #511: what others said about my activity reaches me — my own events are in my feed. */
    @Test
    void myOwnEventCarriesTheReactionsAndCommentsOthersLeft() {
        mutual("alice", "bob");
        String title = "Le mien " + UUID.randomUUID();
        add("bob", title, "READ");
        String event = eventId("bob", title, "FINISHED_READING");

        react("alice", event, "LOVE", 204);
        given().auth().oauth2(token("alice")).contentType("application/json").body("{ \"comment\": \"Bravo !\" }")
                .when().post("/api/feed/" + event + "/comments").then().statusCode(201);

        JsonPath mine = feed("bob");
        String path = "items.find { it.id == '" + event + "' }";
        assertEquals(1, mine.getInt(path + ".reactions.LOVE"));
        assertEquals("Bravo !", mine.getString(path + ".comments[0].comment"));
    }

    /** #513: a book in the feed says whether the viewer holds it, to open their own screen. */
    @Test
    void aFeedBookCarriesTheViewersOwnItem() {
        mutual("alice", "bob");
        String title = "Partagé " + UUID.randomUUID();
        add("bob", title, "READ");
        String mine = add("alice", title, "TO_READ").getString("id");

        assertEquals(mine, feed("alice").getString("items.find { it.book.title == '" + title + "' }.book.myItemId"));
    }
}
