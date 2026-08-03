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

        refresher.refreshNow();

        Series after = QuarkusTransaction.requiringNew().call(() -> series.findById(id));
        assertEquals(Integer.valueOf(20), after.totalVolumes);
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
