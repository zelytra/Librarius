package zelytra.librarius.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** #583: how many costly actions a caller may start in an hour. */
class ActionQuotaTest {

    private static ActionQuota at(Instant now) {
        ActionQuota quota = new ActionQuota();
        quota.useClock(Clock.fixed(now, ZoneOffset.UTC));
        return quota;
    }

    @Test
    void allowsUpToTheLimitThenTellsHowLongToWait() {
        Instant now = Instant.parse("2026-11-20T10:00:00Z");
        ActionQuota quota = at(now);
        assertTrue(quota.check("import", "bob", 2).allowed());
        assertTrue(quota.check("import", "bob", 2).allowed());
        ActionQuota.Decision third = quota.check("import", "bob", 2);
        assertFalse(third.allowed());
        assertEquals(3600, third.retryAfterSeconds());
    }

    @Test
    void countsEachCallerAndEachActionApart() {
        ActionQuota quota = at(Instant.parse("2026-11-20T10:00:00Z"));
        assertTrue(quota.check("import", "bob", 1).allowed());
        assertFalse(quota.check("import", "bob", 1).allowed());
        assertTrue(quota.check("import", "alice", 1).allowed());
        assertTrue(quota.check("export", "bob", 1).allowed());
    }

    @Test
    void startsAfreshOnceTheHourIsOver() {
        Instant start = Instant.parse("2026-11-20T10:00:00Z");
        ActionQuota quota = at(start);
        assertTrue(quota.check("feedback", "bob", 1).allowed());
        assertFalse(quota.check("feedback", "bob", 1).allowed());
        quota.useClock(Clock.fixed(start.plus(Duration.ofMinutes(61)), ZoneOffset.UTC));
        assertTrue(quota.check("feedback", "bob", 1).allowed());
    }
}
