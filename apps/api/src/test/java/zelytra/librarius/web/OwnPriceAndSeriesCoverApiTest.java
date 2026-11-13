package zelytra.librarius.web;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The reader's own price (#431) and the first volume's cover on a series with none (#433). */
@QuarkusTest
class OwnPriceAndSeriesCoverApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    @Inject
    EntityManager em;

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
    void theReaderSetsTheirOwnPriceWithoutTouchingTheEdition() {
        Map<String, Object> item = add("bob", """
                { "book": { "kind": "BOOK", "title": "Own price %s" }, "status": "READ" }
                """.formatted(UUID.randomUUID()));
        String id = (String) item.get("id");
        @SuppressWarnings("unchecked")
        String editionId = (String) ((Map<String, Object>) item.get("book")).get("editionId");
        float before = given().auth().oauth2(token("bob")).when().get("/api/stats/value")
                .then().statusCode(200).extract().jsonPath().getFloat("library");

        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("{ \"price\": 18.50 }")
                .when().put("/api/library/" + id + "/price")
                .then().statusCode(200)
                .body("price", is(18.5f));

        // Shared catalog data stays as it was: the price is the reader's.
        BigDecimal listPrice = QuarkusTransaction.requiringNew().call(() ->
                em.find(zelytra.librarius.domain.Edition.class, UUID.fromString(editionId)).listPrice);
        assertNull(listPrice);
        given().auth().oauth2(token("bob")).when().get("/api/stats/value")
                .then().statusCode(200)
                .body("library", is(before + 18.5f));

        // Someone else's title answers like an unknown one.
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"price\": 1 }")
                .when().put("/api/library/" + id + "/price")
                .then().statusCode(404);

        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("{ \"price\": null }")
                .when().put("/api/library/" + id + "/price")
                .then().statusCode(200)
                .body("price", nullValue());
    }

    @Test
    void aNegativePriceIsRefused() {
        Map<String, Object> item = add("bob", """
                { "book": { "kind": "BOOK", "title": "Negative price %s" } }
                """.formatted(UUID.randomUUID()));
        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("{ \"price\": -3 }")
                .when().put("/api/library/" + item.get("id") + "/price")
                .then().statusCode(400);
    }

    /** #433: a series with no cover wears its first volume's, even one the reader lacks. */
    @Test
    void aSeriesWithNoCoverShowsItsFirstVolumeCover() {
        String title = "Cover run " + UUID.randomUUID();
        // Bob owns volume 1, which carries a cover; alice owns volume 3 only, no cover.
        add("bob", """
                { "book": { "kind": "MANGA", "title": "%s 1", "seriesTitle": "%s", "volumeNumber": 1,
                            "coverUrl": "https://cover.test/first.jpg" } }
                """.formatted(title, title));
        add("alice", """
                { "book": { "kind": "MANGA", "title": "%s 3", "seriesTitle": "%s", "volumeNumber": 3 } }
                """.formatted(title, title));

        String seriesId = given().auth().oauth2(token("alice")).when().get("/api/series")
                .then().statusCode(200)
                .extract().jsonPath().getString("find { it.title == '" + title + "' }.id");
        assertEquals("https://cover.test/first.jpg", given().auth().oauth2(token("alice"))
                .when().get("/api/series")
                .then().extract().jsonPath().getString("find { it.title == '" + title + "' }.coverUrl"));
        given().auth().oauth2(token("alice")).when().get("/api/series/" + seriesId)
                .then().statusCode(200)
                .body("coverUrl", is("https://cover.test/first.jpg"));
    }
}
