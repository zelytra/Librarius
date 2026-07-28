package zelytra.librarius.catalog;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-caller quota over the catalog endpoints.
 *
 * <p>Catalog searches hit Open Library and AniList, whose quotas belong to the instance as
 * a whole: one caller looping over searches would break search for everyone else. Sign-up
 * is open, so a token is not a scarce resource and cannot be treated as trust.
 *
 * <p>Fixed windows rather than a sliding log or a token bucket: a caller can fire a full
 * minute's worth of calls right before a boundary and again right after, but the failure
 * mode is bounded and the implementation holds two counters per user instead of a
 * timestamp history. The point is to protect a quota, not to shape traffic precisely.
 *
 * <p>State is per-instance and in memory. With a single replica that is exact; with
 * several, each enforces the limit independently and the effective ceiling is multiplied
 * by the replica count. Good enough while the limit exists to stop abuse rather than to
 * bill for it — a shared counter would mean Redis, which the stack does not have.
 */
@ApplicationScoped
public class RateLimiter {

    @ConfigProperty(name = "librarius.catalog.rate-limit.per-minute", defaultValue = "30")
    int perMinute;

    @ConfigProperty(name = "librarius.catalog.rate-limit.per-day", defaultValue = "500")
    int perDay;

    @Inject
    MeterRegistry meters;

    /** Overridable so tests can advance time instead of waiting for a window to roll. */
    private Clock clock = Clock.systemUTC();

    private final Map<String, Window> minuteWindows = new ConcurrentHashMap<>();
    private final Map<String, Window> dayWindows = new ConcurrentHashMap<>();

    @PostConstruct
    void registerGauges() {
        meters.gauge("librarius.catalog.rate_limit.tracked_callers", minuteWindows,
                Map::size);
    }

    void useClock(Clock replacement) {
        this.clock = replacement;
        minuteWindows.clear();
        dayWindows.clear();
    }

    /**
     * Records one call for {@code callerId}.
     *
     * @return the decision: allowed, or rejected with the delay to wait for
     */
    public Decision check(String callerId) {
        Instant now = clock.instant();

        Decision minute = consume(minuteWindows, callerId, now, Duration.ofMinutes(1), perMinute);
        if (!minute.allowed()) {
            reject("minute");
            return minute;
        }

        Decision day = consume(dayWindows, callerId, now, Duration.ofDays(1), perDay);
        if (!day.allowed()) {
            // The minute window already counted this call. Refunding it keeps a caller
            // who is blocked for the day from also being told to retry in a minute.
            refund(minuteWindows, callerId);
            reject("day");
            return day;
        }

        return Decision.allow();
    }

    private void reject(String window) {
        meters.counter("librarius.catalog.rate_limit.rejected", "window", window).increment();
    }

    private Decision consume(Map<String, Window> windows, String callerId, Instant now,
            Duration length, int limit) {
        Window window = windows.compute(callerId, (key, current) ->
                current == null || !now.isBefore(current.expiresAt)
                        ? new Window(now.plus(length))
                        : current);

        if (window.count.incrementAndGet() > limit) {
            long retryAfter = Math.max(1, Duration.between(now, window.expiresAt).toSeconds());
            return Decision.deny(retryAfter);
        }
        return Decision.allow();
    }

    private void refund(Map<String, Window> windows, String callerId) {
        Window window = windows.get(callerId);
        if (window != null) {
            window.count.decrementAndGet();
        }
    }

    private static final class Window {
        private final Instant expiresAt;
        private final AtomicInteger count = new AtomicInteger();

        private Window(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }
    }

    /**
     * Outcome of a quota check. {@code retryAfterSeconds} is only set when rejected.
     *
     * <p>The factories are named {@code allow}/{@code deny} rather than
     * {@code allowed}/{@code rejected}: a static method sharing a record component's name
     * hides its accessor, and {@code decision.allowed()} then resolves to the factory.
     */
    public record Decision(boolean allowed, long retryAfterSeconds) {

        static Decision allow() {
            return new Decision(true, 0);
        }

        static Decision deny(long retryAfterSeconds) {
            return new Decision(false, retryAfterSeconds);
        }
    }
}
