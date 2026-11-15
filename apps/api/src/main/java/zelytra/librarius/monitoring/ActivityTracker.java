package zelytra.librarius.monitoring;

import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Notes that a member is using the instance (#491), for the monitoring screen: on an
 * authenticated request, their row of the day in {@code user_activity} is moved forward — at
 * most once a minute per member, and off the request, so that nobody waits for it.
 *
 * <p>After the response, not before: a member's first request is the one that creates their
 * account row, and a write that finds no row yet is not counted against the minute.
 */
@ApplicationScoped
public class ActivityTracker {

    /** How often a member's activity is written at most: well under the five "right now" minutes. */
    static final Duration EVERY = Duration.ofMinutes(1);

    private final Map<String, Instant> lastWritten = new ConcurrentHashMap<>();

    @Inject
    SecurityIdentity identity;

    @Inject
    JsonWebToken jwt;

    @Inject
    EntityManager em;

    @ServerResponseFilter
    public void seen() {
        if (identity.isAnonymous()) {
            return;
        }
        String userId = jwt.getSubject();
        if (userId == null) {
            return;
        }
        Instant now = Instant.now();
        Instant last = lastWritten.get(userId);
        if (last != null && last.plus(EVERY).isAfter(now)) {
            return;
        }
        lastWritten.put(userId, now);
        Infrastructure.getDefaultWorkerPool().execute(() -> write(userId));
    }

    private void write(String userId) {
        try {
            int written = QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery("""
                            insert into user_activity (user_id, day, last_seen_at)
                            select u.id, current_date, now() from app_user u where u.id = :user
                            on conflict (user_id, day) do update set last_seen_at = now()""")
                    .setParameter("user", userId)
                    .executeUpdate());
            if (written == 0) {
                lastWritten.remove(userId);
            }
        } catch (RuntimeException e) {
            // Activity is a statistic: losing one write is no reason to fail anything.
            lastWritten.remove(userId);
            Log.debug("Could not record activity", e);
        }
    }
}
