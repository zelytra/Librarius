package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Adding a title with its status, the collection ordered by what last happened, and the
 * timeline told apart by kind. bob's account is used: the timeline tests of the suite count
 * alice's readings.
 */
@QuarkusTest
class ActivityApiTest {

    private static final KeycloakTestClient KEYCLOAK = new KeycloakTestClient();

    private static String token() {
        return KEYCLOAK.getAccessToken("bob");
    }

    private static String add(String kind, String title, String marker, String status, String acquiredAt) {
        return given().auth().oauth2(token()).contentType("application/json")
                .body("""
                        { "book": { "kind": "%s", "title": "%s", "authors": "%s" },
                          "status": "%s" %s }
                        """.formatted(kind, title, marker, status,
                        acquiredAt == null ? "" : ", \"acquiredAt\": \"" + acquiredAt + "\""))
                .when().post("/api/library").then().statusCode(201).extract().path("id");
    }

    @Test
    void aTitleAddedAsReadIsFinishedTodayAndCounted() {
        String marker = "Read Today " + UUID.randomUUID();
        String id = add("MANGA", "Lu aujourd'hui", marker, "READ", null);

        JsonPath item = given().auth().oauth2(token()).when().get("/api/library/" + id)
                .then().statusCode(200).extract().jsonPath();
        assertEquals("READ", item.getString("status"));
        assertEquals(LocalDate.now().toString(), item.getString("progress.finishedAt"));

        // And the month's bucket says it was a manga.
        String month = "%04d-%02d".formatted(LocalDate.now().getYear(), LocalDate.now().getMonthValue());
        JsonPath timeline = given().auth().oauth2(token())
                .queryParam("from", LocalDate.now().withDayOfYear(1).toString())
                .queryParam("to", LocalDate.now().toString())
                .queryParam("granularity", "month")
                .when().get("/api/stats/timeline").then().statusCode(200).extract().jsonPath();
        List<String> periods = timeline.getList("points.period");
        int index = periods.indexOf(month);
        assertTrue(index >= 0, "the current month has a bucket");
        assertTrue(timeline.getInt("points[" + index + "].byKind.MANGA") >= 1, "counted as a manga");
    }

    @Test
    void theCollectionOpensOnTheLatestActivity() {
        String marker = "Recent " + UUID.randomUUID();
        add("BOOK", "Ajouté il y a longtemps", marker, "OWNED", "2015-06-01");
        add("BOOK", "Lu aujourd'hui", marker, "READ", "2014-01-01");
        add("BOOK", "Ajouté l'an dernier", marker, "OWNED", LocalDate.now().minusYears(1).toString());

        List<String> titles = given().auth().oauth2(token()).queryParam("q", marker)
                .when().get("/api/library").then().statusCode(200).extract().jsonPath()
                .getList("items.book.title");

        // Read today beats added last year, which beats added ten years ago — whatever the
        // order they were entered in.
        assertEquals(List.of("Lu aujourd'hui", "Ajouté l'an dernier", "Ajouté il y a longtemps"), titles);
    }
}
