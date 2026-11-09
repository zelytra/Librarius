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
                .body("items.find { it.book.title == 'La Horde du Contrevent' }.rating", is(5));
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
}
