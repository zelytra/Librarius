package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/** The reader's own page count (#446), the format of their copy (#447), and a new edition (#445). */
@QuarkusTest
class OwnPagesFormatEditionApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    private Map<String, Object> add(String user, String body) {
        return given().auth().oauth2(token(user)).contentType("application/json").body(body)
                .when().post("/api/library")
                .then().statusCode(201)
                .extract().jsonPath().getMap("$");
    }

    @Test
    void theReadersOwnPageCountDrivesTheirFiguresAndStaysTheirs() {
        Map<String, Object> item = add("bob", """
                { "book": { "kind": "BOOK", "title": "Own pages %s", "pageCount": 300 }, "status": "READING" }
                """.formatted(UUID.randomUUID()));
        String id = (String) item.get("id");
        given().auth().oauth2(token("bob")).contentType("application/json").body("{ \"percent\": 50 }")
                .when().put("/api/library/" + id + "/progress").then().statusCode(204);
        long before = given().auth().oauth2(token("bob")).when().get("/api/stats")
                .then().extract().jsonPath().getLong("pagesRead");

        given().auth().oauth2(token("bob")).contentType("application/json").body("{ \"pageCount\": 420 }")
                .when().put("/api/library/" + id + "/pages")
                .then().statusCode(200)
                .body("pageCount", is(420))
                // The edition's own count is left as it was.
                .body("book.pageCount", is(300))
                // The position follows the new length: 50 % of 420.
                .body("progress.currentPage", is(210));

        given().auth().oauth2(token("bob")).contentType("application/json").body("{ \"status\": \"READ\" }")
                .when().put("/api/library/" + id + "/progress").then().statusCode(204);
        given().auth().oauth2(token("bob")).when().get("/api/stats")
                .then().body("pagesRead", is((int) before + 420));

        given().auth().oauth2(token("alice")).contentType("application/json").body("{ \"pageCount\": 1 }")
                .when().put("/api/library/" + id + "/pages").then().statusCode(404);
        given().auth().oauth2(token("bob")).contentType("application/json").body("{ \"pageCount\": 0 }")
                .when().put("/api/library/" + id + "/pages").then().statusCode(400);
    }

    @Test
    void theFormatOfTheCopyIsSetFilteredAndCleared() {
        String title = "Own format " + UUID.randomUUID();
        Map<String, Object> item = add("bob", """
                { "book": { "kind": "BOOK", "title": "%s" }, "format": "POCKET" }
                """.formatted(title));
        String id = (String) item.get("id");
        given().auth().oauth2(token("bob")).when().get("/api/library/" + id)
                .then().body("format", is("POCKET"));
        given().auth().oauth2(token("bob")).contentType("application/json").body("{ \"format\": \"HARDCOVER\" }")
                .when().put("/api/library/" + id + "/format")
                .then().statusCode(200).body("format", is("HARDCOVER"));
        given().auth().oauth2(token("bob")).when().get("/api/library?format=HARDCOVER&q=" + title)
                .then().body("total", is(1));
        given().auth().oauth2(token("bob")).contentType("application/json").body("{}")
                .when().put("/api/library/" + id + "/format")
                .then().statusCode(200).body("format", nullValue());
    }

    @Test
    void aProviderEditionBecomesTheTitlesEditionUnderTheSameWork() {
        Map<String, Object> item = add("bob", """
                { "book": { "kind": "BOOK", "title": "Edition choice %s", "pageCount": 200, "publisher": "Folio" },
                  "status": "READING" }
                """.formatted(UUID.randomUUID()));
        String id = (String) item.get("id");
        @SuppressWarnings("unchecked")
        Map<String, Object> book = (Map<String, Object>) item.get("book");

        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("""
                        { "isbn13": "9782070464241", "publisher": "Gallimard", "pageCount": 400,
                          "format": "Relié", "provider": "openlibrary", "providerRef": "OL9M" }
                        """)
                .when().post("/api/library/" + id + "/edition")
                .then().statusCode(200)
                .body("book.workId", is(book.get("workId")))
                .body("book.editionId", not(is(book.get("editionId"))))
                .body("book.publisher", is("Gallimard"))
                .body("book.isbn13", is("9782070464241"))
                .body("book.pageCount", is(400));

        // Someone else's title cannot be moved.
        given().auth().oauth2(token("alice")).contentType("application/json").body("{ \"publisher\": \"X\" }")
                .when().post("/api/library/" + id + "/edition").then().statusCode(404);
    }
}
