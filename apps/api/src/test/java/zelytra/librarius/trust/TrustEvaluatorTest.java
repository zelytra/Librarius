package zelytra.librarius.trust;

import org.junit.jupiter.api.Test;
import zelytra.librarius.trust.TrustEvaluator.TrustSignals;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The criteria, fabricated fixture by fabricated fixture.
 *
 * <p>No database and no Quarkus: the decision is a pure function of an account's signals, built
 * here with explicit thresholds so it can be pinned down on its own. Each gate is asserted in
 * isolation — an account that fails one criterion while passing the others — so a later retune
 * of one threshold cannot silently switch another off. This is the unit-test half of #180 and
 * #195; the scheduled promotion, the revocation, and the per-user isolation they must respect
 * live in {@link TrustEvaluationTest} and {@link TrustRevocationTest}.
 */
class TrustEvaluatorTest {

    private static final long MIN_MONTHS = 3;
    private static final long MIN_READ = 10;
    private static final long MAX_UPHELD = 3;
    private static final long WINDOW_DAYS = 180;

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    private final TrustEvaluator evaluator =
            new TrustEvaluator(MIN_MONTHS, MIN_READ, MAX_UPHELD, WINDOW_DAYS);

    private static TrustSignals signals(long ageMonths, long readTitles, long upheldReports) {
        return new TrustSignals(NOW.minusMonths(ageMonths), readTitles, upheldReports);
    }

    /** Tenure, volume and a clear record together earn the flag. */
    @Test
    void anAccountMeetingEveryCriterionQualifies() {
        assertTrue(evaluator.qualifies(signals(6, 20, 0), NOW));
    }

    /** On the threshold, not past it: exactly the minimum on each axis still qualifies. */
    @Test
    void meetingTheThresholdsExactlyQualifies() {
        assertTrue(evaluator.qualifies(signals(MIN_MONTHS, MIN_READ, 0), NOW));
    }

    /** Too young: an account below the tenure floor is refused however much it has read. */
    @Test
    void anAccountBelowTheTenureFloorIsRefused() {
        assertFalse(evaluator.qualifies(signals(MIN_MONTHS - 1, 100, 0), NOW));
    }

    /** Too little: an old account with too few finished titles is refused. */
    @Test
    void anAccountBelowTheVolumeFloorIsRefused() {
        assertFalse(evaluator.qualifies(signals(24, MIN_READ - 1, 0), NOW));
    }

    /** Below the upheld-report bar an otherwise fit account still qualifies: a strike or two is
     *  tolerated, and the same gate grants and revokes. */
    @Test
    void belowTheUpheldReportBarAnOtherwiseFitAccountQualifies() {
        assertTrue(evaluator.qualifies(signals(24, 100, MAX_UPHELD - 1), NOW));
    }

    /** Reaching the upheld-report bar takes the flag: this is the verdict revocation reads. */
    @Test
    void reachingTheUpheldReportBarRefusesAnOtherwiseFitAccount() {
        assertFalse(evaluator.qualifies(signals(24, 100, MAX_UPHELD), NOW));
    }
}
