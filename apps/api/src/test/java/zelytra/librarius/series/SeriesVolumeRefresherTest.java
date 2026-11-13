package zelytra.librarius.series;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import zelytra.librarius.catalog.CatalogService;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.Series;
import zelytra.librarius.domain.repository.SeriesRepository;

import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;

/**
 * The refresher writing a provider's volume count onto a series, with the provider mocked so the
 * suite never reaches out to AniList.
 */
@QuarkusTest
class SeriesVolumeRefresherTest {

    @InjectMock
    CatalogService catalog;

    @Inject
    SeriesVolumeRefresher refresher;

    @Inject
    SeriesRepository series;

    @Test
    void writesTheProviderVolumeCountOntoASeriesThatLacksOne() {
        // A title of its own, so the mock below matches this series and no other run's.
        String title = "SVR One Piece " + UUID.randomUUID();
        UUID id = QuarkusTransaction.requiringNew().call(() -> {
            Series s = new Series();
            s.kind = Kind.MANGA;
            s.title = title;
            series.persist(s);
            return s.id;
        });

        // Every other series still missing a total resolves to nothing; only ours reports a count.
        Mockito.when(catalog.seriesVolumes(any(), any())).thenReturn(OptionalInt.empty());
        Mockito.when(catalog.seriesVolumes(Kind.MANGA, title)).thenReturn(OptionalInt.of(20));

        // The never-asked series go first and each run stamps a batch (#429): enough runs reach
        // ours whatever the other tests left behind.
        for (int run = 0; run < 40 && totalOf(id) == null; run++) {
            refresher.refreshNow();
        }

        assertEquals(Integer.valueOf(20), totalOf(id));
    }

    private Integer totalOf(UUID id) {
        return QuarkusTransaction.requiringNew().call(() -> series.findById(id).totalVolumes);
    }

    /** #429: a series no provider knows is stamped, so it no longer holds the others back. */
    @Test
    void stampsASeriesNoProviderKnowsAndDoesNotAskAgainForAWeek() {
        String title = "SVR Unknown " + UUID.randomUUID();
        UUID id = QuarkusTransaction.requiringNew().call(() -> {
            Series s = new Series();
            s.kind = Kind.BOOK;
            s.title = title;
            series.persist(s);
            return s.id;
        });
        Mockito.when(catalog.seriesVolumes(any(), any())).thenReturn(OptionalInt.empty());

        for (int run = 0; run < 40 && checkedAt(id) == null; run++) {
            refresher.refreshNow();
        }
        assertNotNull(checkedAt(id));
        assertNull(totalOf(id));

        Mockito.clearInvocations(catalog);
        refresher.refreshNow();
        Mockito.verify(catalog, Mockito.never()).seriesVolumes(Kind.BOOK, title);
    }

    private java.time.OffsetDateTime checkedAt(UUID id) {
        return QuarkusTransaction.requiringNew().call(() -> series.findById(id).volumesCheckedAt);
    }

    /** #429: right after an import, the reader's own series are resolved first. */
    @Test
    void resolvesTheSeriesOfOneReaderStraightAway() {
        String title = "SVR Mine " + UUID.randomUUID();
        String token = new io.quarkus.test.keycloak.client.KeycloakTestClient().getAccessToken("alice");
        io.restassured.RestAssured.given().auth().oauth2(token).contentType("application/json")
                .body("{ \"book\": { \"kind\": \"BOOK\", \"title\": \"" + title + " 1\", \"seriesTitle\": \""
                        + title + "\", \"volumeNumber\": 1 }, \"status\": \"TO_READ\" }")
                .when().post("/api/library")
                .then().statusCode(201);
        String aliceId = io.restassured.RestAssured.given().auth().oauth2(token).when().get("/api/me")
                .then().statusCode(200).extract().path("id");
        Mockito.when(catalog.seriesVolumes(any(), any())).thenReturn(OptionalInt.empty());
        Mockito.when(catalog.seriesVolumes(Kind.BOOK, title)).thenReturn(OptionalInt.of(7));

        refresher.refreshFor(aliceId);

        Integer total = QuarkusTransaction.requiringNew().call(() ->
                series.find("lower(title) = ?1", title.toLowerCase()).firstResult().totalVolumes);
        assertEquals(Integer.valueOf(7), total);
    }

    @Test
    void leavesASeriesThatAlreadyHasATotalAlone() {
        int wrote = QuarkusTransaction.requiringNew().call(() -> {
            Series s = new Series();
            s.kind = Kind.MANGA;
            s.title = "SVR Already Counted " + UUID.randomUUID();
            s.totalVolumes = 12;
            series.persist(s);
            return refresher.setTotal(s.id, 99);
        });

        assertEquals(0, wrote);
    }
}
