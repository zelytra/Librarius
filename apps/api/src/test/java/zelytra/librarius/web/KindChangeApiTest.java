package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/** A reader filing their title under another kind (#454). */
@QuarkusTest
class KindChangeApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    @Test
    void theTitleMovesToTheKindKeepingWhatTheReaderSetAndOnlyForThem() {
        String title = "Kind change " + UUID.randomUUID();
        String body = """
                { "book": { "kind": "BOOK", "title": "%s", "authors": "Kind Author", "isbn13": "9782505000017",
                  "pageCount": 192 }, "status": "READ" }
                """.formatted(title);
        String bobs = given().auth().oauth2(token("bob")).contentType("application/json").body(body)
                .when().post("/api/library").then().statusCode(201).extract().path("id");
        String alices = given().auth().oauth2(token("alice")).contentType("application/json").body(body)
                .when().post("/api/library").then().statusCode(201).extract().path("id");

        given().auth().oauth2(token("bob")).contentType("application/json").body("{ \"kind\": \"MANGA\" }")
                .when().put("/api/library/" + bobs + "/kind")
                .then().statusCode(200)
                .body("id", is(bobs))
                .body("book.kind", is("MANGA"))
                .body("book.isbn13", is("9782505000017"))
                .body("book.pageCount", is(192))
                .body("status", is("READ"));

        // The library filter follows; another reader's copy keeps its kind.
        given().auth().oauth2(token("bob")).when().get("/api/library?kind=MANGA&q=" + title)
                .then().statusCode(200).body("items.id", hasItem(bobs));
        given().auth().oauth2(token("bob")).when().get("/api/library?kind=BOOK&q=" + title)
                .then().statusCode(200).body("items.id", not(hasItem(bobs)));
        given().auth().oauth2(token("alice")).when().get("/api/library/" + alices)
                .then().statusCode(200).body("book.kind", is("BOOK"));
    }

    @Test
    void anotherReadersTitleOrAnUnknownKindIsRefused() {
        String id = given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"book\": { \"kind\": \"BOOK\", \"title\": \"Kind isolation %s\" } }".formatted(UUID.randomUUID()))
                .when().post("/api/library").then().statusCode(201).extract().path("id");
        given().auth().oauth2(token("bob")).contentType("application/json").body("{ \"kind\": \"COMIC\" }")
                .when().put("/api/library/" + id + "/kind").then().statusCode(404);
        given().auth().oauth2(token("alice")).contentType("application/json").body("{ \"kind\": \"NOVEL\" }")
                .when().put("/api/library/" + id + "/kind").then().statusCode(400);
        given().auth().oauth2(token("alice")).contentType("application/json").body("{}")
                .when().put("/api/library/" + id + "/kind").then().statusCode(400);
    }
}
