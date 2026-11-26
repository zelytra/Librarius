package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

/**
 * {@code POST /api/import/collected}: a library collected in the reader's own browser, read
 * with the same parsers a scrape uses, and imported as a job to poll.
 */
@QuarkusTest
class CollectedImportApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String fixture(String name) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Posts the pages and waits, bounded, for the job to stop running. */
    private Map<String, Object> importAndWait(String user, String source, List<String> pages)
            throws InterruptedException {
        String token = keycloak.getAccessToken(user);
        String id = given().auth().oauth2(token).contentType("application/json")
                .body(Map.of("source", source, "pages", pages))
                .when().post("/api/import/collected")
                .then().statusCode(200)
                .extract().path("id");
        Map<String, Object> job = Map.of();
        for (int i = 0; i < 100; i++) {
            job = given().auth().oauth2(token).when().get("/api/import/jobs/" + id)
                    .then().statusCode(200).extract().as(Map.class);
            if (!"RUNNING".equals(job.get("status"))) {
                break;
            }
            Thread.sleep(100);
        }
        return job;
    }

    @Test
    void importsABooknodeLibraryCollectedInTheBrowser() throws Exception {
        Map<String, Object> job = importAndWait("carol", "booknode",
                List.of(fixture("booknode-biblio.html")));

        org.junit.jupiter.api.Assertions.assertEquals("DONE", job.get("status"));
        given().auth().oauth2(keycloak.getAccessToken("carol"))
                .queryParam("size", 100)
                .when().get("/api/library")
                .then().statusCode(200);
    }

    /** #456: the bookmarklet saves Mangacollec's collection document as the file's one page. */
    @Test
    void importsAMangacollecCollectionCollectedInTheBrowser() throws Exception {
        Map<String, Object> job = importAndWait("carol", "mangacollec",
                List.of(fixture("mangacollec-collection.json")));

        org.junit.jupiter.api.Assertions.assertEquals("DONE", job.get("status"));
        given().auth().oauth2(keycloak.getAccessToken("carol"))
                .queryParam("size", 100).queryParam("q", "Jujutsu Kaisen")
                .when().get("/api/library")
                .then().statusCode(200)
                .body("items.book.title", hasItems("Jujutsu Kaisen Tome 1", "Jujutsu Kaisen Tome 3"));
    }

    @Test
    void importsABabelioLibraryCollectedInTheBrowser() throws Exception {
        Map<String, Object> job = importAndWait("bob", "babelio",
                List.of(fixture("babelio-mes-livres.html")));

        org.junit.jupiter.api.Assertions.assertEquals("DONE", job.get("status"));
        given().auth().oauth2(keycloak.getAccessToken("bob"))
                .queryParam("size", 100).queryParam("q", "Contrevent")
                .when().get("/api/library")
                .then().statusCode(200)
                .body("items.book.title", hasItems("La Horde du Contrevent"))
                .body("items.find { it.book.title == 'La Horde du Contrevent' }.status", is("READ"))
                .body("items.find { it.book.title == 'La Horde du Contrevent' }.rating", is(5.0f));
        // #477: a « Pense-bête » is a wish — it lands on the wishlist, not in the library.
        given().auth().oauth2(keycloak.getAccessToken("bob"))
                .queryParam("size", 100).queryParam("q", "Assassin royal")
                .when().get("/api/wishlist")
                .then().statusCode(200)
                .body("items.book.title", hasItems("L'Assassin royal, tome 1"));
    }

    @Test
    void reportsAFileWithNoLibraryInIt() throws Exception {
        Map<String, Object> job = importAndWait("alice", "babelio",
                List.of("<html><body><p>Connexion</p></body></html>"));

        org.junit.jupiter.api.Assertions.assertEquals("FAILED", job.get("status"));
        org.junit.jupiter.api.Assertions.assertTrue(
                String.valueOf(job.get("error")).startsWith("Aucun livre reconnu"));
    }

    @Test
    void refusesAnUnknownSource() {
        given().auth().oauth2(keycloak.getAccessToken("alice")).contentType("application/json")
                .body(Map.of("source", "goodreads", "pages", List.of("<p></p>")))
                .when().post("/api/import/collected")
                .then().statusCode(400);
    }

    /**
     * #651: the history collected after the library dates its readings by the month — shown as a
     * month, not a day — and a date the reader then enters is a day again.
     */
    @Test
    void theHistoryDatesAReadingByItsMonthUntilTheReaderGivesADay() throws Exception {
        String row = "<div class=\"book-item\"><div class=\"book-name\"><a href=\"https://booknode.com/ancien_roman_0003\">"
                + "Ancien Roman</a></div><div class=\"author-name\"><a>Anne A. Paul P.</a></div>"
                + "<div class=\"list-name\"><div><label class=\"listebadge pasapprecie\">pas apprécié</label></div></div>"
                + "<div class=\"group-name\"><span class=\"group-label\"></span></div></div>";
        org.junit.jupiter.api.Assertions.assertEquals("DONE", importAndWait("bob", "booknode", List.of(row)).get("status"));
        Map<String, Object> dated = importAndWait("bob", "booknode", List.of(fixture("booknode-historique.html")));
        org.junit.jupiter.api.Assertions.assertEquals("DONE", dated.get("status"));
        // Nothing added: the history's other titles are not in this library.
        org.junit.jupiter.api.Assertions.assertEquals(0, dated.get("imported"));

        String token = keycloak.getAccessToken("bob");
        String id = given().auth().oauth2(token).queryParam("q", "Ancien Roman")
                .when().get("/api/library").then().statusCode(200).extract().path("items[0].id");
        given().auth().oauth2(token).when().get("/api/library/" + id).then().statusCode(200)
                .body("progress.finishedAt", org.hamcrest.Matchers.is("2019-08-01"))
                .body("progress.finishedMonthOnly", org.hamcrest.Matchers.is(true));
        given().auth().oauth2(token).when().get("/api/stats/readings?from=2019-08-01&to=2019-08-31")
                .then().statusCode(200)
                .body("find { it.itemId == '" + id + "' }.finishedMonthOnly", org.hamcrest.Matchers.is(true));

        // The dates handed back unchanged keep the month; a day the reader picks replaces it.
        given().auth().oauth2(token).contentType("application/json")
                .body(Map.of("status", "READ", "percent", 100, "finishedAt", "2019-08-01"))
                .when().put("/api/library/" + id + "/progress").then().statusCode(204);
        given().auth().oauth2(token).when().get("/api/library/" + id).then()
                .body("progress.finishedMonthOnly", org.hamcrest.Matchers.is(true));
        given().auth().oauth2(token).contentType("application/json")
                .body(Map.of("status", "READ", "percent", 100, "finishedAt", "2019-08-17"))
                .when().put("/api/library/" + id + "/progress").then().statusCode(204);
        given().auth().oauth2(token).when().get("/api/library/" + id).then()
                .body("progress.finishedAt", org.hamcrest.Matchers.is("2019-08-17"))
                .body("progress.finishedMonthOnly", org.hamcrest.Matchers.is(false));
    }
}
