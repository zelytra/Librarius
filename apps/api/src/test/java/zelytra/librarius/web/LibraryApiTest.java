package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

/**
 * Checks OIDC authentication and — above all — data isolation between users:
 * what Alice creates must never show up for Bob.
 */
@QuarkusTest
class LibraryApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    @Test
    void meRequiresAuthentication() {
        given().when().get("/api/me").then().statusCode(401);
    }

    @Test
    void meReturnsAuthenticatedUser() {
        given().auth().oauth2(token("alice"))
                .when().get("/api/me")
                .then().statusCode(200)
                .body("displayName", is("alice"));
    }

    @Test
    void libraryIsIsolatedBetweenUsers() {
        String aliceItemId = given().auth().oauth2(token("alice"))
                .contentType("application/json")
                .body("""
                        {
                          "book": { "kind": "BOOK", "title": "Fourth Wing", "authors": "Rebecca Yarros" },
                          "status": "READING"
                        }
                        """)
                .when().post("/api/library")
                .then().statusCode(201)
                .body("book.title", is("Fourth Wing"))
                .extract().path("id");

        // Alice sees her item.
        given().auth().oauth2(token("alice"))
                .when().get("/api/library")
                .then().statusCode(200)
                .body("id", hasItem(aliceItemId));

        // Bob does not.
        given().auth().oauth2(token("bob"))
                .when().get("/api/library")
                .then().statusCode(200)
                .body("id", not(hasItem(aliceItemId)));

        // Bob cannot delete Alice's item.
        given().auth().oauth2(token("bob"))
                .when().delete("/api/library/" + aliceItemId)
                .then().statusCode(404);
    }
}
