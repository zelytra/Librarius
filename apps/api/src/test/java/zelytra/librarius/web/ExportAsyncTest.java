package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The deferred half of the export: past a certain number of titles the request hands back a
 * job instead of a file.
 *
 * <p>The threshold is dropped to zero for this class rather than the test creating thousands
 * of titles: the branch is what is under test, not the arithmetic that picks it, and a suite
 * that inserts a large library to exercise a boundary spends minutes proving a comparison.
 */
@QuarkusTest
@TestProfile(ExportAsyncTest.EveryExportDeferred.class)
class ExportAsyncTest {

    /** Zero titles fit in a request, so every export here takes the deferred path. */
    public static class EveryExportDeferred implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("librarius.export.async-threshold", "0");
        }
    }

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    private String startExport(String user) {
        return given().auth().oauth2(token(user)).queryParam("format", "json")
                .when().get("/api/export")
                .then().statusCode(202)
                .body("status", is("PENDING"))
                .body("format", is("json"))
                .header("Location", containsString("/api/export/"))
                .extract().path("id");
    }

    /** Polls the job until the file is there, or gives up rather than hanging the suite. */
    private Response awaitFile(String user, String jobId) {
        for (int attempt = 0; attempt < 100; attempt++) {
            Response response = given().auth().oauth2(token(user))
                    .when().get("/api/export/" + jobId);
            if (response.statusCode() != 202) {
                return response;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("interrupted while waiting for the deferred export");
            }
        }
        return fail("the deferred export never completed");
    }

    @Test
    void aLargeAccountIsExportedInTheBackgroundAndDownloadedAfterwards() {
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "Export - deferred",
                                    "authors": "Async Test" }, "status": "OWNED" }
                        """)
                .when().post("/api/library").then().statusCode(201);

        String jobId = startExport("alice");
        Response file = awaitFile("alice", jobId);

        assertEquals(200, file.statusCode());
        file.then()
                .header("Content-Disposition", containsString("attachment"))
                .body("collection.book.title", hasItem("Export - deferred"));
    }

    /**
     * A job identifier is the only thing standing between a caller and somebody's whole
     * library. Another user's job is 404, exactly like one that never existed.
     */
    @Test
    void aJobBelongingToAnotherUserIsNotFound() {
        String aliceJob = startExport("alice");

        given().auth().oauth2(token("bob"))
                .when().get("/api/export/" + aliceJob)
                .then().statusCode(404);

        // And Alice still has hers.
        assertEquals(200, awaitFile("alice", aliceJob).statusCode());
    }

    @Test
    void anUnknownJobIsNotFound() {
        given().auth().oauth2(token("alice"))
                .when().get("/api/export/" + UUID.randomUUID())
                .then().statusCode(404);
    }
}
