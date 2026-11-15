package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/** The readings of a period, start to finish, for the Stats calendar (#479). */
@QuarkusTest
class ReadingCalendarApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    private String read(String user, String title, String startedAt, String finishedAt) {
        String id = given().auth().oauth2(token(user)).contentType("application/json")
                .body("{ \"book\": { \"kind\": \"BOOK\", \"title\": \"" + title + "\" } }")
                .when().post("/api/library").then().statusCode(201).extract().path("id");
        given().auth().oauth2(token(user)).contentType("application/json")
                .body("{ \"status\": \"READ\", \"startedAt\": \"" + startedAt + "\", \"finishedAt\": \"" + finishedAt + "\" }")
                .when().put("/api/library/" + id + "/progress").then().statusCode(204);
        return id;
    }

    @Test
    void listsTheReadingsFinishedInThePeriodWithHowLongEachTook() {
        String title = "Calendar " + UUID.randomUUID();
        String id = read("bob", title, "2021-03-05", "2021-03-19");
        read("bob", title + " later", "2021-04-02", "2021-04-03");

        given().auth().oauth2(token("bob"))
                .when().get("/api/stats/readings?from=2021-03-01&to=2021-03-31")
                .then().statusCode(200)
                .body("find { it.itemId == '" + id + "' }.title", is(title))
                .body("find { it.itemId == '" + id + "' }.startedAt", is("2021-03-05"))
                .body("find { it.itemId == '" + id + "' }.finishedAt", is("2021-03-19"))
                .body("find { it.itemId == '" + id + "' }.days", is(15))
                .body("title", not(hasItem(title + " later")));

        // Another reader's calendar holds none of it.
        given().auth().oauth2(token("alice"))
                .when().get("/api/stats/readings?from=2021-03-01&to=2021-03-31")
                .then().statusCode(200).body("itemId", not(hasItem(id)));
    }

    @Test
    void refusesAWindowThatEndsBeforeItStarts() {
        given().auth().oauth2(token("bob"))
                .when().get("/api/stats/readings?from=2021-03-31&to=2021-03-01")
                .then().statusCode(400);
    }
}
