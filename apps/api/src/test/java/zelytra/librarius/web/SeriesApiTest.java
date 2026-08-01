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

    /**
     * A volume numbered 0 — a prologue, a "tome 0" — is a volume like any other: owning it
     * must put it in the grid. The run once opened on volume 1 and dropped it, though the
     * counters still counted it, so a series whose only volume was a tome 0 showed an empty
     * grid next to an ownership of one.
     */
    @Test
    void aVolumeZeroTheUserOwnsIsListed() {
        String title = "Series Test - prologue";
        addVolume("alice", title, 0, "OWNED");
        addVolume("alice", title, 1, "READ");

        String id = seriesId("alice", title);

        given().auth().oauth2(token("alice"))
                .when().get("/api/series/" + id)
                .then().statusCode(200)
                .body("ownedCount", is(2))
                // Both the tome 0 and volume 1 are there, in order, each owned.
                .body("volumes.size()", is(2))
                .body("volumes[0].volumeNumber", is(0))
                .body("volumes[0].owned", is(true))
                .body("volumes[1].volumeNumber", is(1))
                .body("volumes[1].read", is(true));
    }

    /**
     * A series whose whole ownership sits at volume 0 must still list that volume rather
     * than the empty grid the volume-1 floor used to produce.
     */
    @Test
    void aSeriesMadeOnlyOfAVolumeZeroIsNotEmpty() {
        String title = "Series Test - only prologue";
        addVolume("alice", title, 0, "OWNED");

        String id = seriesId("alice", title);

        given().auth().oauth2(token("alice"))
                .when().get("/api/series/" + id)
                .then().statusCode(200)
                .body("ownedCount", is(1))
                .body("volumes.size()", is(1))
                .body("volumes[0].volumeNumber", is(0))
                .body("volumes[0].owned", is(true));
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
        given().auth().oauth2(token("alice")).when().get("/api/series/" + unknown + "/review")
                .then().statusCode(404);
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"rating\": 5 }")
                .when().put("/api/series/" + unknown + "/review")
                .then().statusCode(404);
        given().auth().oauth2(token("alice")).when().delete("/api/series/" + unknown + "/review")
                .then().statusCode(404);
    }

    // ── Review (#190) ─────────────────────────────────────────────────────────

    /** A series the caller has neither owned nor followed has no review to write either. */
    @Test
    void aSeriesTheCallerHasNoStakeInCannotBeReviewed() {
        String title = "Series Test - review no stake";
        // Bob owns a volume so the series row exists, but Alice never touches it.
        addVolume("bob", title, 1, "OWNED");
        String id = seriesId("bob", title);

        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"rating\": 4 }")
                .when().put("/api/series/" + id + "/review")
                .then().statusCode(404);
    }

    /** The whole lifecycle: nothing yet, written, read back, updated, then removed. */
    @Test
    void aReviewCanBeWrittenReadUpdatedAndDeleted() {
        String title = "Series Test - review lifecycle";
        addVolume("alice", title, 1, "OWNED");
        String id = seriesId("alice", title);

        given().auth().oauth2(token("alice")).when().get("/api/series/" + id + "/review")
                .then().statusCode(404);

        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"rating\": 4, \"review\": \"Un bon départ.\" }")
                .when().put("/api/series/" + id + "/review")
                .then().statusCode(200)
                .body("rating", is(4))
                .body("review", is("Un bon départ."))
                .body("seriesId", is(id));

        given().auth().oauth2(token("alice")).when().get("/api/series/" + id + "/review")
                .then().statusCode(200)
                .body("rating", is(4))
                .body("review", is("Un bon départ."));

        // A second PUT updates the same row rather than creating another one.
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"rating\": 5, \"review\": \"Encore mieux au tome 2.\" }")
                .when().put("/api/series/" + id + "/review")
                .then().statusCode(200)
                .body("rating", is(5))
                .body("review", is("Encore mieux au tome 2."));

        given().auth().oauth2(token("alice")).when().delete("/api/series/" + id + "/review")
                .then().statusCode(204);

        given().auth().oauth2(token("alice")).when().get("/api/series/" + id + "/review")
                .then().statusCode(404);

        // Deleting again is idempotent, like every other follow-shaped delete here.
        given().auth().oauth2(token("alice")).when().delete("/api/series/" + id + "/review")
                .then().statusCode(204);
    }

    /** An empty text area is the absence of a review text, not a row holding an empty string. */
    @Test
    void aBlankReviewTextIsStoredAsNothing() {
        String title = "Series Test - review blank text";
        addVolume("alice", title, 1, "OWNED");
        String id = seriesId("alice", title);

        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"rating\": 3, \"review\": \"   \" }")
                .when().put("/api/series/" + id + "/review")
                .then().statusCode(200)
                .body("review", nullValue());
    }

    @Test
    void aReviewWithoutARatingIsRejected() {
        String title = "Series Test - review no rating";
        addVolume("alice", title, 1, "OWNED");
        String id = seriesId("alice", title);

        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"review\": \"Pas de note.\" }")
                .when().put("/api/series/" + id + "/review")
                .then().statusCode(400);
    }

    @Test
    void anOutOfRangeSeriesRatingIsRejected() {
        String title = "Series Test - review range";
        addVolume("alice", title, 1, "OWNED");
        String id = seriesId("alice", title);

        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"rating\": 0 }")
                .when().put("/api/series/" + id + "/review")
                .then().statusCode(400);
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"rating\": 6 }")
                .when().put("/api/series/" + id + "/review")
                .then().statusCode(400);
    }
}
