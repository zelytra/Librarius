package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * The two status families of a title (#411): the reading status, and whether the reader has
 * the book — with the wishlist kept in agreement with the latter.
 */
@QuarkusTest
class StatusFamiliesApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    private String add(String user, String title, String status, String ownership) {
        String owned = ownership == null ? "" : ", \"ownership\": \"" + ownership + "\"";
        return given().auth().oauth2(token(user)).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "%s", "authors": "Families Test" },
                          "status": "%s"%s }
                        """.formatted(title, status, owned))
                .when().post("/api/library")
                .then().statusCode(201)
                .extract().path("id");
    }

    private List<String> wishedTitles(String user) {
        return given().auth().oauth2(token(user))
                .when().get("/api/wishlist?size=100&q=Families")
                .then().statusCode(200)
                .extract().path("items.book.title");
    }

    @Test
    void aNewTitleIsOwnedAndOnThePileByDefault() {
        String id = given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"book\": { \"kind\": \"BOOK\", \"title\": \"Families - default\", \"authors\": \"Families Test\" } }")
                .when().post("/api/library")
                .then().statusCode(201)
                .body("status", is("TO_READ"))
                .body("ownership", is("OWNED"))
                .extract().path("id");
        given().auth().oauth2(token("alice")).when().get("/api/library/" + id)
                .then().statusCode(200).body("ownership", is("OWNED"));
    }

    /** A client or an archive written before #411 still says OWNED for the pile. */
    @Test
    void theFormerNameOfThePileIsStillUnderstood() {
        String id = add("alice", "Families - legacy", "OWNED", null);
        given().auth().oauth2(token("alice")).when().get("/api/library/" + id)
                .then().statusCode(200).body("status", is("TO_READ"));
        given().auth().oauth2(token("alice"))
                .when().get("/api/library?status=OWNED&q=Families - legacy")
                .then().statusCode(200).body("total", is(1));
    }

    @Test
    void aBorrowedReadIsRecordedAndFiltered() {
        String id = add("alice", "Families - borrowed", "READ", "BORROWED");
        given().auth().oauth2(token("alice")).when().get("/api/library/" + id)
                .then().statusCode(200)
                .body("status", is("READ"))
                .body("ownership", is("BORROWED"));
        given().auth().oauth2(token("alice"))
                .when().get("/api/library?ownership=BORROWED&q=Families - borrowed")
                .then().statusCode(200).body("total", is(1));
        given().auth().oauth2(token("alice"))
                .when().get("/api/library?ownership=EBOOK&q=Families - borrowed")
                .then().statusCode(200).body("total", is(0));
    }

    @Test
    void wishingATitleOfTheLibraryPutsItOnTheWishlistAndBuyingItTakesItOff() {
        String id = add("alice", "Families - wished", "READ", "BORROWED");

        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"ownership\": \"WISHLIST\" }")
                .when().put("/api/library/" + id + "/ownership")
                .then().statusCode(200).body("ownership", is("WISHLIST"));
        org.hamcrest.MatcherAssert.assertThat(wishedTitles("alice"), hasItem("Families - wished"));

        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"ownership\": \"OWNED\" }")
                .when().put("/api/library/" + id + "/ownership")
                .then().statusCode(200)
                .body("ownership", is("OWNED"))
                .body("status", is("READ"));
        org.hamcrest.MatcherAssert.assertThat(wishedTitles("alice"), not(hasItem("Families - wished")));
    }

    @Test
    void acquiringTheWishOfATitleInTheLibraryKeepsItsHistory() {
        String id = add("alice", "Families - acquired", "READ", "WISHLIST");
        String wishId = given().auth().oauth2(token("alice"))
                .when().get("/api/wishlist?size=100&q=Families - acquired")
                .then().statusCode(200).extract().path("items[0].id");

        given().auth().oauth2(token("alice")).contentType("application/json").body("{}")
                .when().post("/api/wishlist/" + wishId + "/acquire")
                .then().statusCode(201)
                .body("id", is(id))
                .body("status", is("READ"))
                .body("ownership", is("OWNED"));
    }

    @Test
    void droppingTheWishLeavesATitleNotOwned() {
        String id = add("alice", "Families - unwished", "READ", "WISHLIST");
        String wishId = given().auth().oauth2(token("alice"))
                .when().get("/api/wishlist?size=100&q=Families - unwished")
                .then().statusCode(200).extract().path("items[0].id");

        given().auth().oauth2(token("alice")).when().delete("/api/wishlist/" + wishId)
                .then().statusCode(204);
        given().auth().oauth2(token("alice")).when().get("/api/library/" + id)
                .then().statusCode(200).body("ownership", is("NOT_OWNED"));
    }

    /** An audiobook is an ownership of its own, set and filtered like the others (#455). */
    @Test
    void anAudiobookIsAnOwnershipOfItsOwn() {
        String id = add("alice", "Families - audiobook", "READ", null);
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"ownership\": \"AUDIOBOOK\" }")
                .when().put("/api/library/" + id + "/ownership")
                .then().statusCode(200).body("ownership", is("AUDIOBOOK"));
        given().auth().oauth2(token("alice"))
                .when().get("/api/library?ownership=AUDIOBOOK&q=Families - audiobook")
                .then().statusCode(200).body("items.id", org.hamcrest.Matchers.contains(id));
    }

    @Test
    void anotherUsersTitleCannotHaveItsOwnershipChanged() {
        String id = add("alice", "Families - isolation", "TO_READ", null);
        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("{ \"ownership\": \"EBOOK\" }")
                .when().put("/api/library/" + id + "/ownership")
                .then().statusCode(404);
        given().auth().oauth2(token("alice")).when().get("/api/library/" + id)
                .then().statusCode(200).body("ownership", is("OWNED"));
    }

    @Test
    void anUnknownOwnershipIsRefused() {
        String id = add("alice", "Families - unknown", "TO_READ", null);
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"ownership\": \"STOLEN\" }")
                .when().put("/api/library/" + id + "/ownership")
                .then().statusCode(400);
    }
}
