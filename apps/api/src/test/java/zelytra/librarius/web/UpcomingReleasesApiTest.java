package zelytra.librarius.web;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.DatePrecision;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.ReleaseConfidence;
import zelytra.librarius.domain.ReleaseRegion;
import zelytra.librarius.domain.UpcomingRelease;
import zelytra.librarius.domain.repository.SeriesRepository;
import zelytra.librarius.domain.repository.UpcomingReleaseRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code GET /api/releases/upcoming}: the announcements of the series the caller has a
 * stake in.
 *
 * <p>The announcements themselves are catalog data, shared by everyone; what is asserted
 * here is the personalisation on top of it — which series count as the caller's, what an
 * already-owned volume does, and how a date that is not certain survives the round trip
 * without gaining a precision it never had.
 */
@QuarkusTest
class UpcomingReleasesApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    @Inject
    SeriesRepository series;

    @Inject
    UpcomingReleaseRepository releases;

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    /** Adds one volume of a series to the user's collection, which creates the series. */
    private void addVolume(String user, String seriesTitle, int volume) {
        given().auth().oauth2(token(user)).contentType("application/json")
                .body("""
                        { "book": { "kind": "MANGA", "title": "%s vol. %d",
                                    "authors": "Releases Test", "seriesTitle": "%s",
                                    "volumeNumber": %d },
                          "status": "OWNED" }
                        """.formatted(seriesTitle, volume, seriesTitle, volume))
                .when().post("/api/library")
                .then().statusCode(201);
    }

    private UUID seriesId(String user, String seriesTitle) {
        String id = given().auth().oauth2(token(user))
                .when().get("/api/series")
                .then().statusCode(200)
                .extract().path("find { it.title == '" + seriesTitle + "' }.id");
        return UUID.fromString(id);
    }

    /** Stores one announcement, the way the refresher or a curated row would. */
    private UUID announce(UUID seriesId, Integer volume, LocalDate date, DatePrecision precision,
            ReleaseRegion region, String source, ReleaseConfidence confidence) {
        return QuarkusTransaction.requiringNew().call(() -> {
            UpcomingRelease release = new UpcomingRelease();
            release.series = series.findById(seriesId);
            release.volumeNumber = volume;
            release.title = "Tome " + volume;
            release.releaseDate = date;
            release.datePrecision = precision;
            release.region = region;
            release.publisher = "Glénat";
            release.source = source;
            release.confidence = confidence;
            release.updatedAt = OffsetDateTime.now();
            releases.persist(release);
            return release.id;
        });
    }

    /** Announcements returned to a user, as identifiers. */
    private List<String> upcomingIds(String user) {
        return given().auth().oauth2(token(user)).queryParam("limit", 50)
                .when().get("/api/releases/upcoming")
                .then().statusCode(200)
                .extract().jsonPath().getList("id", String.class);
    }

    // ── What makes a release the caller's ─────────────────────────────────────

    /**
     * Owning a volume of a run is a stake in it: the next volume shows up without the user
     * having to do anything else.
     */
    @Test
    void anAnnouncementOfAnOwnedSeriesIsListed() {
        String title = "Releases - possédée";
        addVolume("alice", title, 1);
        UUID id = announce(seriesId("alice", title), 2, LocalDate.now().plusMonths(2),
                DatePrecision.DAY, ReleaseRegion.FR, UpcomingRelease.SOURCE_MANUAL,
                ReleaseConfidence.CONFIRMED);

        assertTrue(upcomingIds("alice").contains(id.toString()),
                "the next volume of a run Alice collects is what she is waiting for");
    }

    /** A wish on a run is a stake in it too — wanting the next volume is what it records. */
    @Test
    void anAnnouncementOfAWishedSeriesIsListed() {
        String title = "Releases - souhaitée";
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("""
                        { "book": { "kind": "MANGA", "title": "%s vol. 1",
                                    "authors": "Releases Test", "seriesTitle": "%s",
                                    "volumeNumber": 1 },
                          "priority": "SOON" }
                        """.formatted(title, title))
                .when().post("/api/wishlist").then().statusCode(201);

        UUID seriesId = QuarkusTransaction.requiringNew()
                .call(() -> series.findByKindAndTitle(Kind.MANGA, title).orElseThrow().id);
        UUID id = announce(seriesId, 2, LocalDate.now().plusMonths(1), DatePrecision.MONTH,
                ReleaseRegion.FR, UpcomingRelease.SOURCE_MANUAL, ReleaseConfidence.CONFIRMED);

        assertTrue(upcomingIds("alice").contains(id.toString()),
                "a wish on a run is a reason to hear about its next volume");
    }

    /** "What is coming" means what is coming <em>for them</em>. */
    @Test
    void aVolumeTheCallerAlreadyOwnsIsNotAnnounced() {
        String title = "Releases - déjà acheté";
        addVolume("alice", title, 1);
        addVolume("alice", title, 2);
        UUID seriesId = seriesId("alice", title);

        UUID owned = announce(seriesId, 2, LocalDate.now().plusMonths(1), DatePrecision.DAY,
                ReleaseRegion.FR, UpcomingRelease.SOURCE_MANUAL, ReleaseConfidence.CONFIRMED);
        UUID next = announce(seriesId, 3, LocalDate.now().plusMonths(3), DatePrecision.DAY,
                ReleaseRegion.FR, UpcomingRelease.SOURCE_MANUAL, ReleaseConfidence.CONFIRMED);

        List<String> ids = upcomingIds("alice");
        assertTrue(ids.contains(next.toString()), "the volume she does not have is still ahead");
        assertTrue(!ids.contains(owned.toString()),
                "a volume already on her shelf is not something she is waiting for");
    }

    /**
     * A run nobody but Alice collects is outside Bob's perimeter, whatever the catalog
     * announces about it. Asserted relatively: the suite shares one database.
     */
    @Test
    void anAnnouncementOfASeriesTheCallerHasNoStakeInIsNotListed() {
        String title = "Releases - hors périmètre";
        addVolume("alice", title, 1);
        UUID id = announce(seriesId("alice", title), 2, LocalDate.now().plusMonths(1),
                DatePrecision.DAY, ReleaseRegion.FR, UpcomingRelease.SOURCE_MANUAL,
                ReleaseConfidence.CONFIRMED);

        given().auth().oauth2(token("bob")).queryParam("limit", 50)
                .when().get("/api/releases/upcoming")
                .then().statusCode(200)
                .body("id", not(hasItem(id.toString())));
    }

    // ── A date that is not certain ────────────────────────────────────────────

    /**
     * A volume announced for a month stays ahead for the whole of it. The anchor is stored
     * on the 1st, so comparing against the anchor alone would drop it on the 2nd.
     */
    @Test
    void aMonthPreciseAnnouncementStaysAheadForTheWholeMonth() {
        String title = "Releases - mois en cours";
        addVolume("alice", title, 1);
        UUID id = announce(seriesId("alice", title), 2,
                LocalDate.now().withDayOfMonth(1), DatePrecision.MONTH, ReleaseRegion.FR,
                UpcomingRelease.SOURCE_MANUAL, ReleaseConfidence.CONFIRMED);

        assertTrue(upcomingIds("alice").contains(id.toString()),
                "'ce mois-ci' is not over on the 2nd");
    }

    /** A day-precise date that has passed is not upcoming any more. */
    @Test
    void aReleaseThatIsAlreadyOutIsDropped() {
        String title = "Releases - déjà sorti";
        addVolume("alice", title, 1);
        UUID id = announce(seriesId("alice", title), 2, LocalDate.now().minusMonths(2),
                DatePrecision.DAY, ReleaseRegion.FR, UpcomingRelease.SOURCE_MANUAL,
                ReleaseConfidence.CONFIRMED);

        assertTrue(!upcomingIds("alice").contains(id.toString()),
                "a volume out two months ago is not an upcoming release");
    }

    /**
     * A volume known to be coming and not known to be dated is listed with no date at all,
     * rather than being dropped or given one.
     */
    @Test
    void anUndatedAnnouncementIsListedWithoutADate() {
        String title = "Releases - sans date";
        addVolume("alice", title, 1);
        UUID id = announce(seriesId("alice", title), 2, null, null, ReleaseRegion.JP,
                "anilist", ReleaseConfidence.ESTIMATED);

        given().auth().oauth2(token("alice")).queryParam("limit", 50)
                .when().get("/api/releases/upcoming")
                .then().statusCode(200)
                .body("find { it.id == '" + id + "' }.releaseDate", is(nullValue()))
                .body("find { it.id == '" + id + "' }.datePrecision", is(nullValue()));
    }

    /**
     * The acceptance criterion of the issue: a date never travels without the market it
     * applies to, how precise it is, and where it comes from.
     */
    @Test
    void everyAnnouncementCarriesItsRegionPrecisionAndSource() {
        String title = "Releases - étiquetage";
        addVolume("alice", title, 1);
        UUID id = announce(seriesId("alice", title), 2, LocalDate.now().plusMonths(4)
                .withDayOfMonth(1), DatePrecision.MONTH, ReleaseRegion.FR,
                UpcomingRelease.SOURCE_MANUAL, ReleaseConfidence.CONFIRMED);

        given().auth().oauth2(token("alice")).queryParam("limit", 50)
                .when().get("/api/releases/upcoming")
                .then().statusCode(200)
                .body("find { it.id == '" + id + "' }.region", is("FR"))
                .body("find { it.id == '" + id + "' }.datePrecision", is("MONTH"))
                .body("find { it.id == '" + id + "' }.confidence", is("CONFIRMED"))
                .body("find { it.id == '" + id + "' }.source", is("manual"))
                .body("find { it.id == '" + id + "' }.seriesTitle", is(title));
    }

    // ── Query parameters ──────────────────────────────────────────────────────

    @Test
    void theKindNarrowsTheListAndTheLimitCapsIt() {
        String title = "Releases - filtres";
        addVolume("alice", title, 1);
        UUID seriesId = seriesId("alice", title);
        announce(seriesId, 2, LocalDate.now().plusMonths(1), DatePrecision.DAY, ReleaseRegion.FR,
                UpcomingRelease.SOURCE_MANUAL, ReleaseConfidence.CONFIRMED);
        announce(seriesId, 3, LocalDate.now().plusMonths(2), DatePrecision.DAY, ReleaseRegion.FR,
                UpcomingRelease.SOURCE_MANUAL, ReleaseConfidence.CONFIRMED);

        // The series was created from a manga, so a book-only list must not hold it.
        given().auth().oauth2(token("alice")).queryParam("kind", "BOOK").queryParam("limit", 50)
                .when().get("/api/releases/upcoming")
                .then().statusCode(200)
                .body("seriesTitle", not(hasItem(title)));

        List<String> capped = given().auth().oauth2(token("alice")).queryParam("limit", 1)
                .when().get("/api/releases/upcoming")
                .then().statusCode(200)
                .extract().jsonPath().getList("id", String.class);
        assertEquals(1, capped.size(), "the limit is what it says");
    }

    @Test
    void theEndpointRejectsAnonymousAccess() {
        given().when().get("/api/releases/upcoming").then().statusCode(401);
    }
}
