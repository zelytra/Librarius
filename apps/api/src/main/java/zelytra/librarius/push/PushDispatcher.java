package zelytra.librarius.push;

import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Decides, once a day, who gets which push notification (#69), and sends it.
 *
 * <ul>
 *   <li><strong>Releases</strong> — a volume dated to the day, of a run the member has a stake
 *       in (a volume owned, a wish, the series or one of its authors followed): a week ahead and
 *       on the day, unless they already own that volume.</li>
 *   <li><strong>Wishlist</strong> — a wish of top priority ({@code PRIORITY}) released today. It
 *       replaces the release message of the same day for that member: one piece of news, worded
 *       as the wish it is.</li>
 *   <li><strong>Goal</strong> — on the first of the month, a reminder of the year's goal.</li>
 * </ul>
 *
 * Every category is opt-in. A message is claimed in {@code push_sent} before it goes out, so a
 * second run — the same day, or on another replica — sends nothing twice.
 */
@ApplicationScoped
public class PushDispatcher {

    /** The day the messages are counted in: the application's readers are French. */
    static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    @Inject
    EntityManager em;

    @Inject
    FcmSender sender;

    /** One message for one member, under the key that makes it sent once. */
    record Pending(String userId, String key, PushMessage message) {
    }

    private record Recipient(String id, String locale) {
        boolean english() {
            return "en".equals(locale);
        }
    }

    private record Announced(UUID id, UUID seriesId, String seriesTitle, Integer volume, LocalDate date) {
    }

    @Scheduled(cron = "{librarius.push.cron}", timeZone = "Europe/Paris",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduled() {
        run(LocalDate.now(ZONE));
    }

    /** The run for {@code today}; public so a test can name the day. */
    public void run(LocalDate today) {
        if (!sender.enabled()) {
            Log.debug("Push: no FCM service account configured, nothing sent");
            return;
        }
        List<Pending> pending = QuarkusTransaction.requiringNew().call(() -> collect(today));
        int sent = 0;
        for (Pending message : pending) {
            if (QuarkusTransaction.requiringNew().call(() -> claim(message))) {
                sent += deliver(message);
            }
        }
        Log.infof("Push: %d message(s) delivered for %s", sent, today);
    }

    private List<Pending> collect(LocalDate today) {
        List<Pending> pending = new ArrayList<>();
        LocalDate weekAhead = today.plusDays(7);
        for (Announced release : announcedOn(today, weekAhead)) {
            boolean releaseDay = release.date().equals(today);
            Set<String> wished = new HashSet<>();
            if (releaseDay && release.volume() != null) {
                for (Recipient r : wishers(release)) {
                    wished.add(r.id());
                    pending.add(new Pending(r.id(), "wish:" + release.id(), wishMessage(release, r)));
                }
            }
            for (Recipient r : stakeholders(release)) {
                if (!wished.contains(r.id())) {
                    pending.add(new Pending(r.id(), "release:" + release.id() + (releaseDay ? ":day" : ":week"),
                            releaseMessage(release, releaseDay, r)));
                }
            }
        }
        if (today.getDayOfMonth() == 1) {
            for (Recipient r : goalHolders(today.getYear())) {
                pending.add(new Pending(r.id(), "goal:" + today.getYear() + "-" + today.getMonthValue(),
                        goalMessage(today.getYear(), r)));
            }
        }
        return pending;
    }

    private List<Announced> announcedOn(LocalDate today, LocalDate weekAhead) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        select r.id, r.series_id, s.title, r.volume_number, r.release_date
                        from upcoming_release r join series s on s.id = r.series_id
                        where r.date_precision = 'DAY' and r.release_date in (:today, :week)""")
                .setParameter("today", today)
                .setParameter("week", weekAhead)
                .getResultList();
        return rows.stream().map(row -> new Announced((UUID) row[0], (UUID) row[1], (String) row[2],
                row[3] == null ? null : ((Number) row[3]).intValue(), toDate(row[4]))).toList();
    }

    /** Opted into releases, with a stake in the run, and not already holding the volume. */
    private List<Recipient> stakeholders(Announced release) {
        return recipients(em.createNativeQuery("""
                        select u.id, u.locale from app_user u
                        where u.push_releases and u.id in (
                            select li.user_id from library_item li
                              join edition e on e.id = li.edition_id join work w on w.id = e.work_id
                            where w.series_id = :series
                            union
                            select wi.user_id from wishlist_item wi
                              join edition e on e.id = wi.edition_id join work w on w.id = e.work_id
                            where w.series_id = :series
                            union
                            select f.user_id from series_follow f where f.series_id = :series
                            union
                            select af.user_id from author_follow af
                              join work_author wa on wa.author_id = af.author_id
                              join work w on w.id = wa.work_id
                            where w.series_id = :series)
                          and not exists (
                            select 1 from library_item li
                              join edition e on e.id = li.edition_id join work w on w.id = e.work_id
                            where li.user_id = u.id and w.series_id = :series
                              and w.volume_number = cast(:volume as integer))""")
                .setParameter("series", release.seriesId())
                .setParameter("volume", release.volume()));
    }

    /** Opted into the wishlist, with that very volume wished at top priority. */
    private List<Recipient> wishers(Announced release) {
        return recipients(em.createNativeQuery("""
                        select distinct u.id, u.locale from app_user u
                          join wishlist_item wi on wi.user_id = u.id
                          join edition e on e.id = wi.edition_id join work w on w.id = e.work_id
                        where u.push_wishlist and wi.priority = 'PRIORITY'
                          and w.series_id = :series and w.volume_number = :volume""")
                .setParameter("series", release.seriesId())
                .setParameter("volume", release.volume()));
    }

    private List<Recipient> goalHolders(int year) {
        return recipients(em.createNativeQuery("""
                        select distinct u.id, u.locale from app_user u
                          join reading_goal g on g.user_id = u.id
                        where u.push_goal and g.year = :year""")
                .setParameter("year", year));
    }

    @SuppressWarnings("unchecked")
    private static List<Recipient> recipients(jakarta.persistence.Query query) {
        return ((List<Object[]>) query.getResultList()).stream()
                .map(row -> new Recipient((String) row[0], (String) row[1])).toList();
    }

    /** Whether this run is the one that sends the message: false when it was already sent. */
    private boolean claim(Pending message) {
        return em.createNativeQuery("""
                        insert into push_sent (user_id, key) values (:user, :key)
                        on conflict do nothing""")
                .setParameter("user", message.userId())
                .setParameter("key", message.key())
                .executeUpdate() == 1;
    }

    /** Sends to each of the member's devices, forgetting those FCM no longer knows. */
    private int deliver(Pending message) {
        @SuppressWarnings("unchecked")
        List<String> devices = QuarkusTransaction.requiringNew().call(() -> em
                .createNativeQuery("select token from push_device where user_id = :user")
                .setParameter("user", message.userId())
                .getResultList());
        int delivered = 0;
        for (String device : devices) {
            FcmSender.Outcome outcome = sender.send(device, message.message());
            if (outcome == FcmSender.Outcome.DELIVERED) {
                delivered++;
            } else if (outcome == FcmSender.Outcome.UNREGISTERED) {
                QuarkusTransaction.requiringNew().run(() -> em
                        .createNativeQuery("delete from push_device where token = :token")
                        .setParameter("token", device)
                        .executeUpdate());
            }
        }
        return delivered;
    }

    // ── Wording: the account's language, French unless it chose English ─────────

    private static PushMessage releaseMessage(Announced release, boolean today, Recipient r) {
        String path = "/series/" + release.seriesId();
        String volume = release.volume() == null ? null : String.valueOf(release.volume());
        if (r.english()) {
            String what = volume == null ? "A new volume" : "Volume " + volume;
            return new PushMessage(release.seriesTitle(),
                    today ? what + " is out today." : what + " is out in 7 days.", path);
        }
        String what = volume == null ? "Un nouveau tome" : "Le tome " + volume;
        return new PushMessage(release.seriesTitle(),
                today ? what + " sort aujourd’hui." : what + " sort dans 7 jours.", path);
    }

    private static PushMessage wishMessage(Announced release, Recipient r) {
        return r.english()
                ? new PushMessage("Your wish is out today", release.seriesTitle() + ", volume " + release.volume(),
                        "/wishlist")
                : new PushMessage("Ton souhait sort aujourd’hui", release.seriesTitle() + ", tome " + release.volume(),
                        "/wishlist");
    }

    private static PushMessage goalMessage(int year, Recipient r) {
        return r.english()
                ? new PushMessage("Your " + year + " goal", "A new month begins: see where you stand.", "/stats")
                : new PushMessage("Ton objectif " + year, "Un nouveau mois commence : regarde où tu en es.", "/stats");
    }

    private static LocalDate toDate(Object value) {
        return value instanceof java.sql.Date date ? date.toLocalDate() : (LocalDate) value;
    }
}
