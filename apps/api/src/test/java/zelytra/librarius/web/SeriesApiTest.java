package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * The {@code /api/series} resource: counters, holes in a run, and follow.
 *
 * <p>Each test uses its own series title: a series is shared catalog data keyed on
 * (kind, title), so two tests reusing a title would land on the same run and interfere.
 */
@QuarkusTest
class SeriesApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    /** Adds one volume of a series to the user's collection. */
    private void addVolume(String user, String seriesTitle, int volume, String status) {
        given().auth().oauth2(token(user)).contentType("application/json")
                .body("""
                        { "book": { "kind": "MANGA", "title": "%s vol. %d",
                                    "authors": "Series Test", "seriesTitle": "%s",
                                    "volumeNumber": %d },
                          "status": "%s" }
                        """.formatted(seriesTitle, volume, seriesTitle, volume, status))
                .when().post("/api/library")
                .then().statusCode(201);
    }

    /** Identifier of a series, read back from the user's series list. */
    private String seriesId(String user, String seriesTitle) {
        return given().auth().oauth2(token(user))
                .when().get("/api/series")
                .then().statusCode(200)
                .extract().path("find { it.title == '" + seriesTitle + "' }.id");
    }

    // ── Counters and holes ────────────────────────────────────────────────────

    /**
     * The acceptance case of the issue: owning volumes 1, 2 and 5 reports 3 and 4 as
     * missing.
     */
    @Test
    void holesInAnOwnedRunAreReported() {
        String title = "Series Test - gaps";
        addVolume("alice", title, 1, "OWNED");
        addVolume("alice", title, 2, "OWNED");
        addVolume("alice", title, 5, "OWNED");

        String id = seriesId("alice", title);

        given().auth().oauth2(token("alice"))
                .when().get("/api/series")
                .then().statusCode(200)
                .body("find { it.id == '" + id + "' }.ownedCount", is(3))
                .body("find { it.id == '" + id + "' }.readCount", is(0))
                .body("find { it.id == '" + id + "' }.followed", is(false));

        given().auth().oauth2(token("alice"))
                .when().get("/api/series/" + id + "/missing")
                .then().statusCode(200)
                .body("volumes", contains(3, 4));

        // The detail lists the whole run, each volume carrying its state.
        given().auth().oauth2(token("alice"))
                .when().get("/api/series/" + id)
                .then().statusCode(200)
                .body("title", is(title))
                .body("ownedCount", is(3))
                .body("volumes.size()", is(5))
                .body("volumes[0].owned", is(true))
                .body("volumes[0].missing", is(false))
                .body("volumes[2].volumeNumber", is(3))
                .body("volumes[2].owned", is(false))
                .body("volumes[2].missing", is(true))
                .body("volumes[2].upcoming", is(false))
                .body("volumes[4].owned", is(true));
    }

    /** A complete run has no hole. */
    @Test
    void aCompleteRunReportsNothingMissing() {
        String title = "Series Test - complete";
        addVolume("alice", title, 1, "READ");
        addVolume("alice", title, 2, "READ");

        String id = seriesId("alice", title);

        given().auth().oauth2(token("alice"))
                .when().get("/api/series/" + id + "/missing")
                .then().statusCode(200)
                .body("volumes", empty());

        given().auth().oauth2(token("alice"))
                .when().get("/api/series/" + id)
                .then().statusCode(200)
                .body("ownedCount", is(2))
                .body("readCount", is(2))
                .body("volumes[0].read", is(true));
    }

    /**
     * Volumes known to the catalog but sitting above the user's run are upcoming, not
     * missing: the catalog is shared, so a volume entered by someone else shows up here.
     */
    @Test
    void volumesAboveTheOwnedRunAreUpcomingRatherThanMissing() {
        String title = "Series Test - upcoming";
        addVolume("alice", title, 1, "OWNED");
        addVolume("alice", title, 2, "OWNED");
        addVolume("bob", title, 4, "OWNED");

        String id = seriesId("alice", title);

        given().auth().oauth2(token("alice"))
                .when().get("/api/series/" + id)
                .then().statusCode(200)
                .body("volumes.size()", is(4))
                .body("volumes[2].volumeNumber", is(3))
                .body("volumes[2].owned", is(false))
                .body("volumes[2].missing", is(false))
                .body("volumes[2].upcoming", is(true))
                // Volume 4 belongs to the shared catalog, so Alice sees its title…
                .body("volumes[3].title", notNullValue())
                // …but it is not hers, and Bob's item never leaks.
                .body("volumes[3].owned", is(false))
                .body("volumes[3].libraryItemId", nullValue())
                .body("volumes[3].upcoming", is(true));

        given().auth().oauth2(token("alice"))
                .when().get("/api/series/" + id + "/missing")
                .then().statusCode(200)
                .body("volumes", empty());
    }

    // ── Follow ────────────────────────────────────────────────────────────────

    /** Following and unfollowing are both idempotent: a retried request must not fail. */
    @Test
    void followingIsIdempotent() {
        String title = "Series Test - follow";
        addVolume("alice", title, 1, "OWNED");
        String id = seriesId("alice", title);

        given().auth().oauth2(token("alice")).when().put("/api/series/" + id + "/follow")
                .then().statusCode(204);
        given().auth().oauth2(token("alice")).when().put("/api/series/" + id + "/follow")
                .then().statusCode(204);

        given().auth().oauth2(token("alice"))
                .when().get("/api/series/" + id)
                .then().statusCode(200)
                .body("followed", is(true));

        given().auth().oauth2(token("alice")).when().delete("/api/series/" + id + "/follow")
                .then().statusCode(204);
        given().auth().oauth2(token("alice")).when().delete("/api/series/" + id + "/follow")
                .then().statusCode(204);

        given().auth().oauth2(token("alice"))
                .when().get("/api/series/" + id)
                .then().statusCode(200)
                .body("followed", is(false));
    }

    /** A followed series stays in the list once its last volume leaves the collection. */
    @Test
    void aFollowedSeriesRemainsListedWithoutAnyVolume() {
        String title = "Series Test - followed only";
        addVolume("alice", title, 1, "OWNED");
        String id = seriesId("alice", title);

        given().auth().oauth2(token("alice")).when().put("/api/series/" + id + "/follow")
                .then().statusCode(204);

        // Drop the only owned volume.
        List<String> itemIds = given().auth().oauth2(token("alice"))
                .queryParam("size", 200).queryParam("q", title)
                .when().get("/api/library")
                .then().statusCode(200)
                .extract().jsonPath().getList("items.id", String.class);
        for (String itemId : itemIds) {
            given().auth().oauth2(token("alice")).when().delete("/api/library/" + itemId)
                    .then().statusCode(204);
        }

        given().auth().oauth2(token("alice"))
                .when().get("/api/series/" + id)
                .then().statusCode(200)
                .body("followed", is(true))
                .body("ownedCount", is(0));
    }

    // ── Unknown identifiers ───────────────────────────────────────────────────

    @Test
    void anUnknownSeriesAnswersNotFound() {
        String unknown = UUID.randomUUID().toString();

        given().auth().oauth2(token("alice")).when().get("/api/series/" + unknown)
                .then().statusCode(404);
        given().auth().oauth2(token("alice")).when().get("/api/series/" + unknown + "/missing")
                .then().statusCode(404);
        given().auth().oauth2(token("alice")).when().put("/api/series/" + unknown + "/follow")
                .then().statusCode(404);
        given().auth().oauth2(token("alice")).when().delete("/api/series/" + unknown + "/follow")
                .then().statusCode(404);
    }
}
