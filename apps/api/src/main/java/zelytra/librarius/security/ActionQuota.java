package zelytra.librarius.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * How many costly actions a caller may start in an hour (#583): an import scrapes a provider,
 * an export builds a whole library, a report writes a row. Self-registration is open, so being
 * signed in bounds nothing; the catalog has its own, finer quota ({@code catalog.RateLimiter}).
 *
 * <p>Fixed hourly windows, in memory, per replica — the same trade the catalog limiter makes
 * and documents: the point is to stop a loop, not to bill a caller exactly.
 */
@ApplicationScoped
public class ActionQuota {

    private Clock clock = Clock.systemUTC();
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /** Overridable so tests can move time instead of waiting an hour. */
    void useClock(Clock replacement) {
        this.clock = replacement;
    }

    /** Records one {@code action} for {@code callerId}, allowed up to {@code perHour} an hour. */
    public Decision check(String action, String callerId, int perHour) {
        Instant now = clock.instant();
        // A window that has run out is replaced, so the map holds the callers of the last hour.
        windows.values().removeIf(w -> !now.isBefore(w.expiresAt));
        Window window = windows.computeIfAbsent(action + '|' + callerId,
                key -> new Window(now.plus(Duration.ofHours(1))));
        if (window.count.incrementAndGet() > perHour) {
            return new Decision(false, Math.max(1, Duration.between(now, window.expiresAt).toSeconds()));
        }
        return new Decision(true, 0);
    }

    /**
     * The same check, answering a refused call with a 429, its {@code Retry-After} and a message
     * the reader can read — the API's user-facing copy is French (CONVENTIONS § 1).
     */
    public void enforce(String action, String callerId, int perHour, String message) {
        Decision decision = check(action, callerId, perHour);
        if (!decision.allowed()) {
            throw new ClientErrorException(Response.status(429)
                    .header("Retry-After", decision.retryAfterSeconds())
                    .entity(Map.of("message", message))
                    .type(MediaType.APPLICATION_JSON)
                    .build());
        }
    }

    private static final class Window {
        private final Instant expiresAt;
        private final AtomicInteger count = new AtomicInteger();

        private Window(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }
    }

    /** Whether the call may go ahead, and if not how many seconds until it may. */
    public record Decision(boolean allowed, long retryAfterSeconds) {
    }
}
