package zelytra.librarius.releases;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import zelytra.librarius.catalog.CatalogResult;
import zelytra.librarius.domain.DatePrecision;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.ReleaseConfidence;
import zelytra.librarius.domain.ReleaseRegion;
import zelytra.librarius.domain.Series;
import zelytra.librarius.domain.UpcomingRelease;
import zelytra.librarius.domain.Work;
import zelytra.librarius.domain.repository.EditionRepository;
import zelytra.librarius.domain.repository.SeriesRepository;
import zelytra.librarius.domain.repository.UpcomingReleaseRepository;
import zelytra.librarius.domain.repository.WorkRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What fills {@code upcoming_release}.
 *
 * <p>The feeds are exercised directly rather than through the scheduler — which the test
 * profile switches off — and with fabricated provider results rather than a real call: what
 * is worth locking down is the mapping, that is, which market a date belongs to, how much of
 * it is real, and what beats what when two sources describe the same volume.
 */
@QuarkusTest
class UpcomingReleaseRefresherTest {

    @Inject
    UpcomingReleaseRefresher refresher;

    @Inject
    SeriesRepository series;

    @Inject
    WorkRepository works;

    @Inject
    EditionRepository editions;

    @Inject
    UpcomingReleaseRepository releases;

    /** A series of the shared catalog, with no volume attached. */
    private UUID seedSeries(String title) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Series created = new Series();
            created.kind = Kind.MANGA;
            created.title = title;
            series.persist(created);
            return created.id;
        });
    }

    /** A volume of that series, in one edition, published on a given day and language. */
    private void seedEdition(UUID seriesId, int volume, LocalDate releaseDate, String language) {
        QuarkusTransaction.requiringNew().run(() -> {
            Work work = new Work();
            work.kind = Kind.MANGA;
            work.title = "Refresh vol. " + volume;
            work.series = series.findById(seriesId);
            work.seriesTitle = work.series.title;
            work.volumeNumber = volume;
            works.persist(work);

            Edition edition = new Edition();
            edition.work = work;
            edition.language = language;
            edition.publisher = "Ki-oon";
            edition.releaseDate = releaseDate;
            editions.persist(edition);
        });
    }

    private Optional<UpcomingRelease> stored(UUID seriesId, Integer volume, ReleaseRegion region) {
        return QuarkusTransaction.requiringNew()
                .call(() -> releases.findAnnouncement(seriesId, volume, region));
    }

    private long announcementCount(UUID seriesId) {
        return QuarkusTransaction.requiringNew()
                .call(() -> releases.count("series.id = ?1", seriesId));
    }

    private static CatalogResult result(String title, LocalDate releaseDate, Integer year) {
        return new CatalogResult("MANGA", title, "Refresh Test", year, null, null, null,
                null, null, releaseDate, "anilist", "1");
    }

    // ── The provider feed ─────────────────────────────────────────────────────

    /**
     * AniList announces the original edition, and announces what it expects rather than
     * what an editor committed to: the row says so on both counts.
     */
    @Test
    void aProviderAnnouncementIsFiledAgainstTheOriginalEdition() {
        String title = "Refresh - fournisseur";
        UUID seriesId = seedSeries(title);
        LocalDate date = LocalDate.now().plusMonths(6);

        int written = QuarkusTransaction.requiringNew()
                .call(() -> refresher.ingestProvider(Kind.MANGA, List.of(result(title, date, null))));

        assertEquals(1, written);
        UpcomingRelease release = stored(seriesId, null, ReleaseRegion.JP).orElseThrow();
        assertEquals(ReleaseRegion.JP, release.region);
        assertEquals(ReleaseConfidence.ESTIMATED, release.confidence);
        assertEquals(DatePrecision.DAY, release.datePrecision);
        assertEquals(date, release.releaseDate);
        assertEquals("anilist", release.source);
    }

    /**
     * A provider that only knows the year must not be turned into a day. The anchor sits on
     * 1 January and the precision says the rest, so no screen can print a day nobody knows.
     */
    @Test
    void aYearOnlyAnnouncementKeepsTheYearAsItsPrecision() {
        String title = "Refresh - année seule";
        UUID seriesId = seedSeries(title);
        int year = LocalDate.now().getYear() + 2;

        QuarkusTransaction.requiringNew()
                .call(() -> refresher.ingestProvider(Kind.MANGA, List.of(result(title, null, year))));

        UpcomingRelease release = stored(seriesId, null, ReleaseRegion.JP).orElseThrow();
        assertEquals(DatePrecision.YEAR, release.datePrecision);
        assertEquals(LocalDate.of(year, 1, 1), release.releaseDate);
        assertTrue(release.stillAhead(LocalDate.now()), "a year still to come is still ahead");
    }

    /** Knowing nothing about the date is stored as nothing, not as today. */
    @Test
    void anAnnouncementWithNoDateAtAllIsStoredWithoutOne() {
        String title = "Refresh - aucune date";
        UUID seriesId = seedSeries(title);

        QuarkusTransaction.requiringNew()
                .call(() -> refresher.ingestProvider(Kind.MANGA, List.of(result(title, null, null))));

        UpcomingRelease release = stored(seriesId, null, ReleaseRegion.JP).orElseThrow();
        assertNull(release.releaseDate);
        assertNull(release.datePrecision);
        assertTrue(release.stillAhead(LocalDate.now()),
                "a volume known to be coming and not known to be out is still ahead");
    }

    /** An announcement about a run the catalog does not hold interests nobody here. */
    @Test
    void anAnnouncementMatchingNoKnownSeriesIsDropped() {
        int written = QuarkusTransaction.requiringNew().call(() -> refresher.ingestProvider(
                Kind.MANGA, List.of(result("Refresh - série inconnue au bataillon", null, null))));

        assertEquals(0, written);
    }

    /** Running the feed twice updates the row rather than piling up duplicates. */
    @Test
    void refreshingTwiceUpdatesTheSameAnnouncement() {
        String title = "Refresh - deux passages";
        UUID seriesId = seedSeries(title);
        LocalDate first = LocalDate.now().plusMonths(3);
        LocalDate postponed = first.plusMonths(1);

        QuarkusTransaction.requiringNew()
                .call(() -> refresher.ingestProvider(Kind.MANGA, List.of(result(title, first, null))));
        QuarkusTransaction.requiringNew().call(() ->
                refresher.ingestProvider(Kind.MANGA, List.of(result(title, postponed, null))));

        assertEquals(postponed, stored(seriesId, null, ReleaseRegion.JP).orElseThrow().releaseDate);
        assertEquals(1L, announcementCount(seriesId),
                "one announcement per volume and per market, however many refreshes run");
    }

    // ── The catalog feed ──────────────────────────────────────────────────────

    /**
     * The only source of French dates the application has: an edition in French whose
     * publication date is still ahead is a French release, to the day, announced by whoever
     * entered it.
     */
    @Test
    void aFrenchEditionDatedAheadBecomesAFrenchAnnouncement() {
        String title = "Refresh - édition française";
        UUID seriesId = seedSeries(title);
        LocalDate date = LocalDate.now().plusMonths(2);
        seedEdition(seriesId, 12, date, "fr");

        QuarkusTransaction.requiringNew().call(() -> refresher.ingestCatalogEditions());

        UpcomingRelease release = stored(seriesId, 12, ReleaseRegion.FR).orElseThrow();
        assertEquals(date, release.releaseDate);
        assertEquals(DatePrecision.DAY, release.datePrecision);
        assertEquals(ReleaseConfidence.CONFIRMED, release.confidence);
        assertEquals(UpcomingRelease.SOURCE_CATALOG, release.source);
        assertEquals("Ki-oon", release.publisher);
    }

    /**
     * An edition whose language names no market we can label is dropped: showing a date
     * without saying which edition it belongs to is the very ambiguity the table removes.
     */
    @Test
    void anEditionWithNoUsableLanguageIsSkipped() {
        String title = "Refresh - langue inconnue";
        UUID seriesId = seedSeries(title);
        seedEdition(seriesId, 3, LocalDate.now().plusMonths(2), null);

        QuarkusTransaction.requiringNew().call(() -> refresher.ingestCatalogEditions());

        assertEquals(0L, announcementCount(seriesId), "no region, no announcement");
    }

    // ── Curated rows win ──────────────────────────────────────────────────────

    /** A hand-checked French date beats anything a feed deduces, run after run. */
    @Test
    void aCuratedAnnouncementSurvivesARefresh() {
        String title = "Refresh - saisie manuelle";
        UUID seriesId = seedSeries(title);
        LocalDate curated = LocalDate.now().plusMonths(5);
        seedEdition(seriesId, 7, LocalDate.now().plusMonths(9), "fr");

        QuarkusTransaction.requiringNew().run(() -> {
            UpcomingRelease release = new UpcomingRelease();
            release.series = series.findById(seriesId);
            release.volumeNumber = 7;
            release.releaseDate = curated;
            release.datePrecision = DatePrecision.DAY;
            release.region = ReleaseRegion.FR;
            release.publisher = "Kana";
            release.source = UpcomingRelease.SOURCE_MANUAL;
            release.confidence = ReleaseConfidence.CONFIRMED;
            release.updatedAt = OffsetDateTime.now();
            releases.persist(release);
        });

        QuarkusTransaction.requiringNew().call(() -> refresher.ingestCatalogEditions());

        UpcomingRelease release = stored(seriesId, 7, ReleaseRegion.FR).orElseThrow();
        assertEquals(curated, release.releaseDate, "the curated date is the one that stands");
        assertEquals(UpcomingRelease.SOURCE_MANUAL, release.source);
    }
}
