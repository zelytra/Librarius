package zelytra.librarius.web;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import zelytra.librarius.catalog.CatalogResult;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.releases.UpcomingReleaseRefresher;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * #615: following an author or a series means hearing of its next volume. A {@code RELEASE}
 * notification is written once per reader the first time an announcement appears — through an
 * admin's curated date or through the background refresh — and points at that announcement.
 */
@QuarkusTest
class ReleaseNotificationApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    @Inject
    UpcomingReleaseRefresher refresher;

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    /** Alice adds volume 1 of a run, by "{title} Author"; returns the series and the author. */
    private UUID[] seedRun(String title) {
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("""
                        { "book": { "kind": "MANGA", "title": "%s vol. 1", "authors": "%s Author",
                                    "seriesTitle": "%s", "volumeNumber": 1 },
                          "status": "TO_READ" }
                        """.formatted(title, title, title))
                .when().post("/api/library").then().statusCode(201);
        String series = given().auth().oauth2(token("alice")).when().get("/api/series")
                .then().statusCode(200)
                .extract().path("find { it.title == '" + title + "' }.id");
        String author = given().auth().oauth2(token("alice")).queryParam("q", title + " Author")
                .when().get("/api/authors").then().statusCode(200).extract().path("[0].id");
        return new UUID[] {UUID.fromString(series), UUID.fromString(author)};
    }

    private void followAuthor(String user, UUID author) {
        given().auth().oauth2(token(user)).when().put("/api/authors/" + author + "/follow")
                .then().statusCode(204);
    }

    private void followSeries(String user, UUID series) {
        given().auth().oauth2(token(user)).when().put("/api/series/" + series + "/follow")
                .then().statusCode(204);
    }

    private String curate(UUID series, int volume) {
        return given().auth().oauth2(token("alice")).contentType("application/json")
                .body("""
                        { "seriesId": "%s", "volumeNumber": %d, "releaseDate": "%s" }
                        """.formatted(series, volume, LocalDate.now().plusWeeks(2)))
                .when().post("/api/admin/releases").then().statusCode(201).extract().path("id");
    }

    /** The release notifications of {@code user} about the run. */
    private List<Map<String, Object>> releasesOf(String user, UUID series) {
        return given().auth().oauth2(token(user)).queryParam("size", 100).when().get("/api/notifications")
                .then().statusCode(200).extract().jsonPath()
                .getList("items.findAll { it.type == 'RELEASE' && it.release.seriesId == '" + series + "' }");
    }

    @Test
    void aFollowerOfTheAuthorHearsOfTheNextVolume() {
        UUID[] run = seedRun("Notif sortie auteur " + UUID.randomUUID());
        followAuthor("bob", run[1]);

        String release = curate(run[0], 2);

        List<Map<String, Object>> mine = releasesOf("bob", run[0]);
        assertEquals(1, mine.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> about = (Map<String, Object>) mine.get(0).get("release");
        assertEquals(release, about.get("id"));
        assertEquals(2, about.get("volumeNumber"));
        assertNull(mine.get(0).get("actor"), "a release is nobody's doing");
    }

    @Test
    void aFollowerOfTheSeriesHearsOfIt() {
        UUID[] run = seedRun("Notif sortie série " + UUID.randomUUID());
        followSeries("alice", run[0]);

        curate(run[0], 2);

        assertEquals(1, releasesOf("alice", run[0]).size());
    }

    @Test
    void aReaderWhoFollowsNeitherHearsNothing() {
        UUID[] run = seedRun("Notif sortie personne " + UUID.randomUUID());
        followAuthor("bob", run[1]);

        curate(run[0], 2);

        assertEquals(List.of(), releasesOf("carol", run[0]));
    }

    /** Following both the run and its author is still one reason to hear of a volume. */
    @Test
    void followingTheSeriesAndItsAuthorIsOneNotification() {
        UUID[] run = seedRun("Notif sortie double " + UUID.randomUUID());
        followAuthor("alice", run[1]);
        followSeries("alice", run[0]);

        curate(run[0], 2);

        assertEquals(1, releasesOf("alice", run[0]).size());
    }

    /** A date corrected later is the same announcement, not a second piece of news. */
    @Test
    void anUpdatedAnnouncementDoesNotNotifyAgain() {
        UUID[] run = seedRun("Notif sortie corrigée " + UUID.randomUUID());
        followAuthor("bob", run[1]);

        curate(run[0], 2);
        curate(run[0], 2);

        assertEquals(1, releasesOf("bob", run[0]).size());
    }

    @Test
    void theBackgroundRefreshNotifiesToo() {
        String title = "Notif sortie refresh " + UUID.randomUUID();
        UUID[] run = seedRun(title);
        followAuthor("bob", run[1]);
        CatalogResult announced = new CatalogResult("MANGA", title, title + " Author", null, null, null,
                null, null, null, LocalDate.now().plusMonths(3), "anilist", "1");

        QuarkusTransaction.requiringNew().call(() -> refresher.ingestProvider(Kind.MANGA, List.of(announced)));
        QuarkusTransaction.requiringNew().call(() -> refresher.ingestProvider(Kind.MANGA, List.of(announced)));

        assertEquals(1, releasesOf("bob", run[0]).size());
    }
}
