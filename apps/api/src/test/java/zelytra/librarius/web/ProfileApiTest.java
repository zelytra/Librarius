package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static zelytra.librarius.web.SocialTestSupport.add;
import static zelytra.librarius.web.SocialTestSupport.id;
import static zelytra.librarius.web.SocialTestSupport.mutual;
import static zelytra.librarius.web.SocialTestSupport.publicAccount;
import static zelytra.librarius.web.SocialTestSupport.relation;
import static zelytra.librarius.web.SocialTestSupport.shareReview;
import static zelytra.librarius.web.SocialTestSupport.strangers;
import static zelytra.librarius.web.SocialTestSupport.token;

/** #497: a member's profile — who they are to anyone, what they read to whoever may see it. */
@QuarkusTest
class ProfileApiTest {

    private static JsonPath profile(String viewer, String member) {
        return given().auth().oauth2(token(viewer)).when().get("/api/users/" + id(member) + "/profile")
                .then().statusCode(200).extract().jsonPath();
    }

    @BeforeEach
    void noRelationship() {
        strangers();
    }

    @Test
    void aStrangerSeesTheNameAndTheCountsButNotTheReading() {
        relation("carol", "bob", "follow", true);
        JsonPath bob = profile("alice", "bob");

        assertEquals(id("bob"), bob.getString("id"));
        assertNotNull(bob.getString("displayName"));
        assertFalse(bob.getBoolean("visible"));
        assertNull(bob.get("reading"), "a private account shows no reading to a stranger");
        assertFalse(bob.getBoolean("following"));
        assertFalse(bob.getBoolean("followsYou"));
        assertFalse(bob.getBoolean("self"));
        assertTrue(bob.getInt("followers") >= 1);
    }

    @Test
    void aOneWayFollowRevealsNothingAndAMutualOneEverything() {
        String title = "Profil " + UUID.randomUUID();
        String finished = add("bob", title, "READ").getString("id");
        add("bob", "En cours " + UUID.randomUUID(), "READING");
        shareReview("bob", finished, 4.5, "Superbe");

        relation("alice", "bob", "follow", true);
        JsonPath oneWay = profile("alice", "bob");
        assertTrue(oneWay.getBoolean("following"));
        assertNull(oneWay.get("reading"));

        relation("bob", "alice", "follow", true);
        JsonPath bob = profile("alice", "bob");
        assertTrue(bob.getBoolean("visible"));
        assertTrue(bob.getBoolean("followsYou"));
        assertTrue(bob.getInt("reading.read") >= 1);
        assertTrue(bob.getInt("reading.reading") >= 1);
        assertTrue(bob.getInt("reading.readThisYear") >= 1);
        assertEquals(title, bob.getString("reading.recentlyFinished.find { it.title == '" + title + "' }.title"));
        assertTrue(bob.getList("reading.currentlyReading").size() >= 1);
        assertEquals("Superbe", bob.getString("reading.reviews.find { it.title == '" + title + "' }.review"));
        assertTrue(bob.getList("reading.topGenres").size() >= 1);
    }

    @Test
    void aPrivateReviewNeverShowsOnTheProfile() {
        String title = "Privé " + UUID.randomUUID();
        String item = add("bob", title, "READ").getString("id");
        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("{ \"rating\": 1, \"review\": \"Secret\", \"shared\": false }")
                .when().put("/api/library/" + item + "/review").then().statusCode(200);
        mutual("alice", "bob");

        JsonPath bob = profile("alice", "bob");
        assertNull(bob.get("reading.reviews.find { it.title == '" + title + "' }"));
        assertNull(bob.get("reading.recentlyFinished.find { it.title == '" + title + "' }.rating"),
                "a rating kept private is not shown either");
    }

    @Test
    void aPublicAccountIsReadByAnyMember() {
        publicAccount("bob", true);
        assertTrue(profile("carol", "bob").getBoolean("visible"));
        assertNotNull(profile("carol", "bob").get("reading"));
    }

    @Test
    void aBlockEitherWayAndAnUnknownIdAnswer404() {
        publicAccount("bob", true);
        relation("bob", "alice", "block", true);
        given().auth().oauth2(token("alice")).when().get("/api/users/" + id("bob") + "/profile").then().statusCode(404);
        given().auth().oauth2(token("bob")).when().get("/api/users/" + id("alice") + "/profile").then().statusCode(404);
        given().auth().oauth2(token("alice")).when().get("/api/users/nobody-" + UUID.randomUUID() + "/profile")
                .then().statusCode(404);
        given().when().get("/api/users/" + id("bob") + "/profile").then().statusCode(401);
    }

    @Test
    void aMemberSeesTheirOwnProfileInFull() {
        JsonPath me = profile("carol", "carol");
        assertTrue(me.getBoolean("self"));
        assertTrue(me.getBoolean("visible"));
        assertNotNull(me.get("reading"));
    }
}
