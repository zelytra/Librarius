package zelytra.librarius.feedback;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import zelytra.librarius.domain.Feedback;
import zelytra.librarius.domain.Feedback.Kind;
import zelytra.librarius.domain.Feedback.Source;
import zelytra.librarius.domain.Feedback.Status;
import zelytra.librarius.web.ApiDtos.AgentFeedbackPageDto;
import zelytra.librarius.web.ApiDtos.FeedbackDetailDto;
import zelytra.librarius.web.ApiDtos.FeedbackDto;
import zelytra.librarius.web.ApiDtos.FeedbackPageDto;
import zelytra.librarius.web.PageRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reports (#489, #490): a member's, written once each, and errors, one row per fingerprint
 * counted each time it comes back — a loop failing on every render is one line, not a flood.
 */
@ApplicationScoped
public class FeedbackService {

    /** The longest text kept for a message and for details, whatever the source sends. */
    static final int MAX_MESSAGE = 5000;
    static final int MAX_DETAILS = 20000;

    @Inject
    EntityManager em;

    @Transactional
    public UUID submit(String userId, Kind kind, String message, String page, String appVersion,
            String userAgent, String logs) {
        Feedback feedback = new Feedback();
        feedback.id = UUID.randomUUID();
        feedback.userId = userId;
        feedback.kind = kind;
        feedback.source = Source.USER;
        feedback.message = message.strip();
        feedback.details = blankToNull(logs);
        feedback.page = page;
        feedback.appVersion = appVersion;
        feedback.userAgent = userAgent;
        feedback.createdAt = OffsetDateTime.now();
        feedback.lastSeenAt = feedback.createdAt;
        em.persist(feedback);
        return feedback.id;
    }

    /**
     * Records an error, or counts it once more when its {@code signature} — what makes two
     * occurrences the same error — was seen before. One statement, so that two requests failing
     * at once count twice rather than racing to insert. An error an admin marked handled that
     * comes back is reopened as new (#495); one set aside as irrelevant stays aside. A caller with no account yet is left
     * out rather than failing the insert. In a transaction of its own: the one of
     * the request that failed is being rolled back.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordError(Source source, String userId, String signature, String message, String details,
            String page, String appVersion, String userAgent) {
        em.createNativeQuery("""
                        insert into feedback (id, user_id, kind, source, message, details, page, app_version,
                                              user_agent, fingerprint, occurrences, created_at, last_seen_at)
                        values (:id, (select u.id from app_user u where u.id = :user), 'ERROR', :source, :message, :details, :page, :version,
                                :agent, :fingerprint, 1, now(), now())
                        on conflict (fingerprint) where fingerprint is not null
                        do update set occurrences = feedback.occurrences + 1, last_seen_at = now(),
                                      status = case when feedback.status = 'HANDLED' then 'NEW' else feedback.status end,
                                      details = excluded.details, page = excluded.page,
                                      app_version = excluded.app_version, user_agent = excluded.user_agent""")
                .setParameter("id", UUID.randomUUID())
                .setParameter("user", userId)
                .setParameter("source", source.name())
                .setParameter("message", clip(message, MAX_MESSAGE))
                .setParameter("details", clip(details, MAX_DETAILS))
                .setParameter("page", clip(page, 1024))
                .setParameter("version", clip(appVersion, 64))
                .setParameter("agent", clip(userAgent, 512))
                .setParameter("fingerprint", fingerprint(source.name() + '|' + signature))
                .executeUpdate();
    }

    /** The reports, the latest seen first; only those in {@code status} when it is given (#495). */
    @Transactional
    public FeedbackPageDto list(PageRequest window, Status status) {
        var rows = rows(window, status).stream().map(row -> {
            Feedback f = (Feedback) row[0];
            return new FeedbackDto(f.id, f.kind.name(), f.source.name(), f.message, f.page, f.appVersion,
                    (String) row[1], f.occurrences, f.createdAt, f.lastSeenAt, f.status.name(), f.statusChangedAt);
        }).toList();
        return new FeedbackPageDto(rows, window.page(), window.size(), count(status));
    }

    /**
     * The same list with each report in full, for an agent (#518). Reading it changes nothing:
     * a report stays new until someone says otherwise.
     */
    @Transactional
    public AgentFeedbackPageDto listInFull(PageRequest window, Status status) {
        var rows = rows(window, status).stream().map(row -> detail((Feedback) row[0], (String) row[1])).toList();
        return new AgentFeedbackPageDto(rows, window.page(), window.size(), count(status));
    }

    /** One page of reports with their reporter's display name, the latest seen first. */
    private List<Object[]> rows(PageRequest window, Status status) {
        var query = em.createQuery("""
                        select f, u.displayName from Feedback f left join AppUser u on u.id = f.userId"""
                        + filter(status) + " order by f.lastSeenAt desc, f.id", Object[].class);
        if (status != null) {
            query.setParameter("status", status);
        }
        return query.setFirstResult(window.offset()).setMaxResults(window.size()).getResultList();
    }

    /** How many reports are in {@code status}, every one when null — #635 reads the NEW ones. */
    public long count(Status status) {
        var query = em.createQuery("select count(f) from Feedback f" + filter(status), Long.class);
        if (status != null) {
            query.setParameter("status", status);
        }
        return query.getSingleResult();
    }

    private static String filter(Status status) {
        return status == null ? "" : " where f.status = :status";
    }

    private static FeedbackDetailDto detail(Feedback f, String reporterName) {
        return new FeedbackDetailDto(f.id, f.kind.name(), f.source.name(), f.message, f.page, f.appVersion,
                f.userAgent, f.details, reporterName, f.occurrences, f.createdAt, f.lastSeenAt, f.status.name(),
                f.statusChangedAt);
    }

    /** One report in full. An admin opening a new one has now seen it (#495). */
    @Transactional
    public Optional<FeedbackDetailDto> open(UUID id) {
        return em.createQuery("""
                        select f, u.displayName from Feedback f left join AppUser u on u.id = f.userId
                        where f.id = :id""", Object[].class)
                .setParameter("id", id)
                .getResultStream()
                .findFirst()
                .map(row -> {
                    Feedback f = (Feedback) row[0];
                    if (f.status == Status.NEW) {
                        changeStatus(f, Status.SEEN);
                    }
                    return detail(f, (String) row[1]);
                });
    }

    /** Sets a report's triage status (#495); false when there is no such report. */
    @Transactional
    public boolean setStatus(UUID id, Status status) {
        Feedback feedback = em.find(Feedback.class, id);
        if (feedback == null) {
            return false;
        }
        changeStatus(feedback, status);
        return true;
    }

    private static void changeStatus(Feedback feedback, Status status) {
        feedback.status = status;
        feedback.statusChangedAt = OffsetDateTime.now();
    }

    static String fingerprint(String signature) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(signature.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String clip(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
