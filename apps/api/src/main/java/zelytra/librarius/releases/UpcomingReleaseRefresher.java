package zelytra.librarius.releases;

import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import zelytra.librarius.catalog.CatalogResult;
import zelytra.librarius.catalog.CatalogService;
import zelytra.librarius.domain.DatePrecision;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.ReleaseConfidence;
import zelytra.librarius.domain.ReleaseRegion;
import zelytra.librarius.domain.Series;
import zelytra.librarius.domain.UpcomingRelease;
import zelytra.librarius.domain.repository.EditionRepository;
import zelytra.librarius.domain.repository.SeriesRepository;
import zelytra.librarius.domain.repository.UpcomingReleaseRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Fills {@code upcoming_release}, off the request path.
 *
 * <p>This is the whole point of the table: a screen showing "what is coming" must cost a
 * query, not a call to a provider. The Open Library and AniList quotas belong to the
 * instance as a whole, and displaying that section on every render used to spend them per
 * user and per visit.
 *
 * <p>Two feeds, and a third that outranks both:
 *
 * <ol>
 *   <li><strong>The providers</strong> — {@link CatalogService#upcoming} behind its
 *       two-level cache. AniList announces the <em>original</em> edition, so those rows are
 *       {@code JP} and {@code ESTIMATED}, and are attached to a series only when the catalog
 *       already holds one under that title. An announcement matching no known series is
 *       dropped: nobody here collects it.</li>
 *   <li><strong>Our own catalog</strong> — an {@link Edition} whose publication date is
 *       still ahead. Those are real per-volume dates, on a market their language names, and
 *       they are the only source of <em>French</em> dates the application has: no free API
 *       covers French publishers.</li>
 *   <li><strong>Curated rows</strong> ({@code source = 'manual'}), entered by hand. A
 *       refresh never touches them — a checked French date beats anything deduced.</li>
 * </ol>
 *
 * <p>Nothing written here is user-scoped: it is catalog data.
 * {@link UpcomingReleaseService} is what turns it into one reader's list.
 */
@ApplicationScoped
public class UpcomingReleaseRefresher {

    /** Announcements pulled from the providers per kind on each run. */
    private static final int PROVIDER_BATCH = 50;

    /** How long a released announcement is kept before the purge reclaims its row. */
    private static final int KEEP_RELEASED_DAYS = 90;

    @Inject
    CatalogService catalog;

    @Inject
    SeriesRepository series;

    @Inject
    EditionRepository editions;

    @Inject
    UpcomingReleaseRepository releases;

    /**
     * Refreshes the announcements.
     *
     * <p>Daily by default, and switched off by setting the interval to {@code off} — which
     * is what the test profile does, so the suite never reaches out to a provider.
     * {@code SKIP} means a run overrunning its interval is not doubled up.
     */
    @Scheduled(every = "{librarius.releases.refresh.every}",
            delayed = "{librarius.releases.refresh.delayed}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledRefresh() {
        int written = refreshNow();
        long purged = QuarkusTransaction.requiringNew()
                .call(() -> releases.purgeReleased(LocalDate.now().minusDays(KEEP_RELEASED_DAYS)));
        Log.infof("Upcoming releases: %d announcement(s) written, %d purged", written, purged);
    }

    /**
     * Runs both feeds and returns how many announcements were created or updated.
     *
     * <p>Each write phase opens its own transaction, and the provider is called outside of
     * them: a run that fails on one kind still commits what the other produced, and no
     * database connection is held open across an outbound HTTP call.
     */
    public int refreshNow() {
        int written = 0;
        for (Kind kind : Kind.values()) {
            List<CatalogResult> announced = catalog.upcoming(kind, PROVIDER_BATCH);
            written += QuarkusTransaction.requiringNew()
                    .call(() -> ingestProvider(kind, announced));
        }
        written += QuarkusTransaction.requiringNew().call(this::ingestCatalogEditions);
        return written;
    }

    /**
     * Turns what a provider announces into announcements of series the catalog knows.
     *
     * <p>Takes the results as an argument rather than fetching them: it is the mapping that
     * is worth locking down — which market, which precision, what becomes of a fuzzy date —
     * and a test can hand it fabricated results without a provider and without a mock.
     *
     * <p>Must run inside a transaction.
     */
    public int ingestProvider(Kind kind, List<CatalogResult> results) {
        int written = 0;
        for (CatalogResult result : results) {
            Optional<Series> match = series.findByKindAndTitle(kind, result.title());
            if (match.isEmpty()) {
                // The catalog holds no run under that title: nobody here is waiting for it.
                continue;
            }
            Dated dated = datedFrom(result);
            boolean stored = upsert(match.get(), null, result.title(), dated, ReleaseRegion.JP,
                    result.publisher(), providerName(result),
                    // A provider announces what it expects, not what an editor committed to.
                    ReleaseConfidence.ESTIMATED);
            written += stored ? 1 : 0;
        }
        return written;
    }

    /**
     * Turns the editions of the catalog dated in the future into announcements.
     *
     * <p>An edition whose language names no market we can label is skipped: a date shown
     * without the edition it belongs to is exactly the ambiguity this table removes, and
     * inventing a region would be worse than dropping the row.
     *
     * <p>Must run inside a transaction.
     */
    public int ingestCatalogEditions() {
        int written = 0;
        for (Edition edition : editions.announcedFrom(LocalDate.now())) {
            Optional<ReleaseRegion> region = ReleaseRegion.ofLanguage(edition.language);
            if (region.isEmpty()) {
                continue;
            }
            boolean stored = upsert(edition.work.series, edition.work.volumeNumber,
                    edition.work.title, new Dated(edition.releaseDate, DatePrecision.DAY),
                    region.get(), edition.publisher, UpcomingRelease.SOURCE_CATALOG,
                    // A date carried by an edition is a publication date, not a projection.
                    ReleaseConfidence.CONFIRMED);
            written += stored ? 1 : 0;
        }
        return written;
    }

    /**
     * Creates or updates the announcement for a volume on a market.
     *
     * @return whether anything was written — a curated row is left alone and counts for
     *         nothing
     */
    private boolean upsert(Series target, Integer volumeNumber, String title, Dated dated,
            ReleaseRegion region, String publisher, String source, ReleaseConfidence confidence) {
        Optional<UpcomingRelease> existing =
                releases.findAnnouncement(target.id, volumeNumber, region);
        if (existing.filter(UpcomingRelease::isManual).isPresent()) {
            return false;
        }
        UpcomingRelease release = existing.orElseGet(UpcomingRelease::new);
        release.series = target;
        release.volumeNumber = volumeNumber;
        release.title = title;
        release.releaseDate = dated.date();
        release.datePrecision = dated.precision();
        release.region = region;
        release.publisher = publisher;
        release.source = source;
        release.confidence = confidence;
        release.updatedAt = OffsetDateTime.now();
        if (existing.isEmpty()) {
            releases.persist(release);
        }
        return true;
    }

    /**
     * A date and how much of it is real.
     *
     * <p>Nothing is invented here: a provider that only knows the year yields a {@code YEAR}
     * anchored on 1 January, and one that knows nothing yields no date at all rather than
     * today or the start of the century.
     */
    private record Dated(LocalDate date, DatePrecision precision) {
    }

    private static Dated datedFrom(CatalogResult result) {
        if (result.releaseDate() != null) {
            return new Dated(result.releaseDate(), DatePrecision.DAY);
        }
        if (result.year() != null) {
            return new Dated(LocalDate.of(result.year(), 1, 1), DatePrecision.YEAR);
        }
        return new Dated(null, null);
    }

    private static String providerName(CatalogResult result) {
        return result.provider() != null ? result.provider() : UpcomingRelease.SOURCE_CATALOG;
    }
}
