package zelytra.librarius.catalog;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Quota arithmetic, driven by a controllable clock so the windows can be rolled without
 * the test sleeping for a minute.
 */
@QuarkusTest
class RateLimiterTest {

    @Inject
    RateLimiter limiter;

    @ConfigProperty(name = "librarius.catalog.rate-limit.per-minute")
    int perMinute;

    /**
     * Caller identifiers that cannot collide with the Dev Services accounts. The limiter
     * is application-scoped and shared by the whole suite: exhausting `alice`'s quota
     * here would make CatalogResourceTest fail with a 429 on an unrelated assertion.
     */
    private static final String CALLER = "rate-limit-test-caller";
    private static final String OTHER_CALLER = "rate-limit-test-other";

    /** Hands the real clock back, so later tests are not stuck at a frozen instant. */
    @AfterEach
    void restoreClock() {
        limiter.useClock(Clock.systemUTC());
    }

    /** Mutable clock: `instant` is advanced by the test. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-07-28T10:00:00Z");

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }
    }

    @Test
    void allowsCallsUpToTheMinuteLimitThenRejects() {
        TestClock clock = new TestClock();
        limiter.useClock(clock);

        for (int i = 0; i < perMinute; i++) {
            assertTrue(limiter.check(CALLER).allowed(), "call " + (i + 1) + " should pass");
        }

        RateLimiter.Decision rejected = limiter.check(CALLER);
        assertFalse(rejected.allowed());
        assertTrue(rejected.retryAfterSeconds() > 0, "a rejection must say when to retry");
        assertTrue(rejected.retryAfterSeconds() <= 60);
    }

    @Test
    void countsEachCallerSeparately() {
        TestClock clock = new TestClock();
        limiter.useClock(clock);

        for (int i = 0; i < perMinute; i++) {
            limiter.check(CALLER);
        }

        // One caller exhausting their share must not affect another.
        assertFalse(limiter.check(CALLER).allowed());
        assertTrue(limiter.check(OTHER_CALLER).allowed());
    }

    @Test
    void allowsCallsAgainOnceTheWindowRolls() {
        TestClock clock = new TestClock();
        limiter.useClock(clock);

        for (int i = 0; i < perMinute; i++) {
            limiter.check(CALLER);
        }
        assertFalse(limiter.check(CALLER).allowed());

        clock.advance(Duration.ofMinutes(1).plusSeconds(1));

        assertTrue(limiter.check(CALLER).allowed(), "a new window starts fresh");
    }

    @Test
    void retryAfterShrinksAsTheWindowElapses() {
        TestClock clock = new TestClock();
        limiter.useClock(clock);

        for (int i = 0; i < perMinute; i++) {
            limiter.check(CALLER);
        }
        long atStart = limiter.check(CALLER).retryAfterSeconds();

        clock.advance(Duration.ofSeconds(30));
        long later = limiter.check(CALLER).retryAfterSeconds();

        assertTrue(later < atStart, "the wait must shrink as the window elapses");
        assertEquals(30, atStart - later);
    }
}
