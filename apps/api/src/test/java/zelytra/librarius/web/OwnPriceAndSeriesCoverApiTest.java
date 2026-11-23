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

    /**
     * #636: a book's price is the shared record's list price, corrected through an edit proposal.
     * There is no personal price to set any more, and one stored before no longer counts. On
     * carol's account: bob's value is summed in floats by the value tests.
     */
    @Test
    void theValueCountsTheSharedPriceAndAStoredPersonalOneIsIgnored() {
        Map<String, Object> item = add("carol", """
                { "book": { "kind": "BOOK", "title": "Shared price %s" }, "status": "READ" }
                """.formatted(UUID.randomUUID()));
        String id = (String) item.get("id");
        @SuppressWarnings("unchecked")
        String editionId = (String) ((Map<String, Object>) item.get("book")).get("editionId");
        given().auth().oauth2(token("carol")).contentType("application/json").body("{ \"price\": 18.50 }")
                .when().put("/api/library/" + id + "/price").then().statusCode(404);

        float before = given().auth().oauth2(token("carol")).when().get("/api/stats/value")
                .then().statusCode(200).extract().jsonPath().getFloat("library");
        // A personal price stored before #636 stays in its column, read by nothing.
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("update library_item set price = 18.50 where id = cast(?1 as uuid)")
                        .setParameter(1, id).executeUpdate());
        given().auth().oauth2(token("carol")).when().get("/api/stats/value")
                .then().statusCode(200).body("library", is(before));

        // The shared list price, set through the record correction (alice is an admin), counts.
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body(Map.of("targetType", "EDITION", "targetId", editionId, "fields", Map.of("listPrice", "12.40")))
                .when().post("/api/catalog-edits").then().statusCode(201);
        given().auth().oauth2(token("carol")).when().get("/api/stats/value")
                .then().statusCode(200).body("library", is(before + 12.4f));
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
