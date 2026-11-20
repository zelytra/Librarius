package zelytra.librarius.social;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.repository.LibraryItemRepository;
import zelytra.librarius.web.ApiDtos.MemberBookDto;
import zelytra.librarius.web.ApiDtos.FeedCommentDto;
import zelytra.librarius.web.ApiDtos.FeedItemDto;
import zelytra.librarius.web.ApiDtos.FeedPageDto;
import zelytra.librarius.web.ApiDtos.MemberSummaryDto;
import zelytra.librarius.web.PageRequest;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The social feed (#209, #210): what the members a reader follows did, and the reactions and
 * comments on it.
 *
 * <p>Events are logged where the action happens — a title finished, a wish added, a review
 * shared — once per member, type and edition. Imports and bulk edits log nothing: a library
 * of five hundred titles arriving at once is not news, it is a flood.
 *
 * <p>Who sees what is {@link VisibilityGate}'s rule and nobody else's: a viewer's feed holds their
 * own events (#511) and those of the members they follow and may see (a mutual follow, or a public account, never
 * across a block). Reacting and commenting re-check the same rule against the event's author —
 * an event outlives the follow that made it visible, and a stale screen must not write on it.
 */
@ApplicationScoped
public class FeedService {

    public enum Type { FINISHED_READING, WISHLIST_ADDED, REVIEW_SHARED }

    /** Comments shown per event, the oldest first. */
    static final int COMMENTS_PER_EVENT = 50;

    @Inject
    EntityManager em;

    @Inject
    VisibilityGate gate;

    @Inject
    LibraryItemRepository libraryItems;

    @Inject
    NotificationService notifications;

    /**
     * Logs that {@code userId} did {@code type} on an edition; a second time is a no-op. A new
     * event notifies the author's followers (#516).
     */
    public void record(String userId, Type type, UUID editionId) {
        UUID id = UUID.randomUUID();
        int inserted = em.createNativeQuery("""
                        insert into feed_event (id, user_id, type, edition_id, created_at)
                        values (:id, :user, :type, :edition, now())
                        on conflict (user_id, type, edition_id) do nothing""")
                .setParameter("id", id)
                .setParameter("user", userId)
                .setParameter("type", type.name())
                .setParameter("edition", editionId)
                .executeUpdate();
        if (inserted > 0) {
            notifications.activity(userId, id);
        }
    }

    @Transactional
    public FeedPageDto feed(String viewerId, PageRequest window) {
        // The caller's own events too (#511): what others said about them belongs in their feed.
        List<String> actors = new ArrayList<>(List.of(viewerId));
        em.createQuery("select f.followeeId from UserFollow f where f.followerId = :viewer", String.class)
                .setParameter("viewer", viewerId)
                .getResultList()
                .stream()
                .filter(actor -> gate.canView(viewerId, actor))
                .forEach(actors::add);
        if (actors.isEmpty()) {
            return new FeedPageDto(List.of(), window.page(), window.size(), false);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(SELECT_EVENTS + """
                         where fe.user_id in (:actors)""" + STILL_SHARED + " order by fe.created_at desc, fe.id")
                .setParameter("actors", actors)
                .setFirstResult(window.offset())
                .setMaxResults(window.size() + 1)
                .getResultList();
        boolean hasMore = rows.size() > window.size();
        List<Object[]> page = hasMore ? rows.subList(0, window.size()) : rows;
        return new FeedPageDto(items(viewerId, page), window.page(), window.size(), hasMore);
    }

    /**
     * One event, as the screen a notification opens shows it (#516) — with its reactions and
     * comments; empty when it is unknown or its author is out of the caller's reach.
     */
    @Transactional
    public Optional<FeedItemDto> one(String viewerId, UUID eventId) {
        if (!canActOn(viewerId, eventId)) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(SELECT_EVENTS + " where fe.id = :id" + STILL_SHARED)
                .setParameter("id", eventId)
                .getResultList();
        return items(viewerId, rows).stream().findFirst();
    }

    /** Every event shown, with its author, its book and the shared review when it is one. */
    private static final String SELECT_EVENTS = """
            select fe.id, fe.type, fe.created_at, u.id as uid, u.display_name, u.trusted,
                   e.id as eid, w.id as wid, w.kind, w.title, w.authors, e.cover_url,
                   li.rating, li.review, li.review_shared, u.avatar_version,
                   p.started_at, p.finished_at
            from feed_event fe
              join app_user u on u.id = fe.user_id
              join edition e on e.id = fe.edition_id
              join work w on w.id = e.work_id
              left join library_item li on li.user_id = fe.user_id and li.edition_id = fe.edition_id
              left join reading_progress p on p.library_item_id = li.id
            """;

    /** A review event whose review is no longer shared, or empty, is gone with it. */
    private static final String STILL_SHARED = """

              and (fe.type <> 'REVIEW_SHARED'
                   or (li.review_shared and (li.rating is not null or li.review is not null)))
            """;

    private List<FeedItemDto> items(String viewerId, List<Object[]> page) {
        List<UUID> ids = page.stream().map(row -> (UUID) row[0]).toList();
        Map<UUID, Map<String, Long>> reactions = reactions(ids);
        Map<UUID, String> mine = myReactions(viewerId, ids);
        Map<UUID, List<FeedCommentDto>> comments = comments(viewerId, ids);
        // The viewer's own copy of each work, so a book opens their Detail when they hold it (#513).
        Map<UUID, UUID> myItems = libraryItems.itemIdsByWork(viewerId,
                page.stream().map(row -> (UUID) row[7]).distinct().toList());

        List<FeedItemDto> items = new ArrayList<>();
        for (Object[] row : page) {
            UUID id = (UUID) row[0];
            boolean review = Type.REVIEW_SHARED.name().equals(row[1]);
            items.add(new FeedItemDto(id, (String) row[1], toOffset(row[2]),
                    new MemberSummaryDto((String) row[3], (String) row[4], Boolean.TRUE.equals(row[5]), toLong(row[15])),
                    MemberBookDto.of(em.find(Edition.class, row[6]), myItems.get((UUID) row[7])),
                    review ? toDouble(row[12]) : null, review ? (String) row[13] : null,
                    reactions.getOrDefault(id, Map.of()), mine.get(id), comments.getOrDefault(id, List.of()),
                    Type.FINISHED_READING.name().equals(row[1]) ? readingDays(row[16], row[17]) : null));
        }
        return items;
    }

    /** Sets the caller's reaction, replacing the one they had; false when the event is out of reach. */
    @Transactional
    public boolean react(String viewerId, UUID eventId, String emote) {
        if (!canActOn(viewerId, eventId)) {
            return false;
        }
        em.createNativeQuery("""
                        insert into feed_reaction (feed_event_id, user_id, emote, created_at)
                        values (:event, :user, :emote, now())
                        on conflict (feed_event_id, user_id) do update set emote = excluded.emote, created_at = now()""")
                .setParameter("event", eventId)
                .setParameter("user", viewerId)
                .setParameter("emote", emote)
                .executeUpdate();
        notifications.reaction(viewerId, eventId, emote);
        return true;
    }

    @Transactional
    public boolean unreact(String viewerId, UUID eventId) {
        if (!canActOn(viewerId, eventId)) {
            return false;
        }
        em.createNativeQuery("delete from feed_reaction where feed_event_id = :event and user_id = :user")
                .setParameter("event", eventId)
                .setParameter("user", viewerId)
                .executeUpdate();
        return true;
    }

    /** Adds a comment; empty when the event is out of reach. */
    @Transactional
    public Optional<UUID> comment(String viewerId, UUID eventId, String comment) {
        if (!canActOn(viewerId, eventId)) {
            return Optional.empty();
        }
        UUID id = UUID.randomUUID();
        em.createNativeQuery("""
                        insert into feed_comment (id, feed_event_id, user_id, comment, created_at)
                        values (:id, :event, :user, :comment, now())""")
                .setParameter("id", id)
                .setParameter("event", eventId)
                .setParameter("user", viewerId)
                .setParameter("comment", comment.strip())
                .executeUpdate();
        notifications.comment(viewerId, eventId, id);
        return Optional.of(id);
    }

    /** Deletes one of the caller's own comments; false when there is no such comment of theirs. */
    @Transactional
    public boolean deleteComment(String viewerId, UUID eventId, UUID commentId) {
        return em.createNativeQuery(
                        "delete from feed_comment where id = :id and feed_event_id = :event and user_id = :user")
                .setParameter("id", commentId)
                .setParameter("event", eventId)
                .setParameter("user", viewerId)
                .executeUpdate() > 0;
    }

    private boolean canActOn(String viewerId, UUID eventId) {
        @SuppressWarnings("unchecked")
        List<String> actor = em.createNativeQuery("select user_id from feed_event where id = :id")
                .setParameter("id", eventId)
                .getResultList();
        return !actor.isEmpty() && gate.canView(viewerId, actor.get(0));
    }

    private Map<UUID, Map<String, Long>> reactions(List<UUID> ids) {
        Map<UUID, Map<String, Long>> byEvent = new HashMap<>();
        if (ids.isEmpty()) {
            return byEvent;
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        select feed_event_id, emote, count(*) from feed_reaction
                        where feed_event_id in (:ids) group by feed_event_id, emote""")
                .setParameter("ids", ids)
                .getResultList();
        for (Object[] row : rows) {
            byEvent.computeIfAbsent((UUID) row[0], k -> new LinkedHashMap<>())
                    .put((String) row[1], ((Number) row[2]).longValue());
        }
        return byEvent;
    }

    private Map<UUID, String> myReactions(String viewerId, List<UUID> ids) {
        Map<UUID, String> mine = new HashMap<>();
        if (ids.isEmpty()) {
            return mine;
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "select feed_event_id, emote from feed_reaction where feed_event_id in (:ids) and user_id = :user")
                .setParameter("ids", ids)
                .setParameter("user", viewerId)
                .getResultList();
        rows.forEach(row -> mine.put((UUID) row[0], (String) row[1]));
        return mine;
    }

    /** The comments of each event, minus those of anyone a block stands between with the viewer. */
    private Map<UUID, List<FeedCommentDto>> comments(String viewerId, List<UUID> ids) {
        Map<UUID, List<FeedCommentDto>> byEvent = new HashMap<>();
        if (ids.isEmpty()) {
            return byEvent;
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        select c.feed_event_id, c.id, u.id as uid, u.display_name, u.trusted, c.comment, c.created_at,
                               u.avatar_version
                        from feed_comment c join app_user u on u.id = c.user_id
                        where c.feed_event_id in (:ids)
                          and not exists (select 1 from user_block b
                                          where (b.blocker_id = :viewer and b.blocked_id = c.user_id)
                                             or (b.blocker_id = c.user_id and b.blocked_id = :viewer))
                        order by c.created_at, c.id""")
                .setParameter("ids", ids)
                .setParameter("viewer", viewerId)
                .getResultList();
        for (Object[] row : rows) {
            List<FeedCommentDto> list = byEvent.computeIfAbsent((UUID) row[0], k -> new ArrayList<>());
            if (list.size() < COMMENTS_PER_EVENT) {
                String author = (String) row[2];
                list.add(new FeedCommentDto((UUID) row[1],
                        new MemberSummaryDto(author, (String) row[3], Boolean.TRUE.equals(row[4]), toLong(row[7])),
                        (String) row[5], toOffset(row[6]), author.equals(viewerId)));
            }
        }
        return byEvent;
    }

    private static OffsetDateTime toOffset(Object value) {
        if (value instanceof OffsetDateTime odt) {
            return odt;
        }
        if (value instanceof java.time.Instant instant) {
            return instant.atOffset(java.time.ZoneOffset.UTC);
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
        }
        return null;
    }

    /**
     * How long a finished book was read (#569): both days counted, as the reading sheet counts
     * them; null without a start date — a book added as read has none — or when the dates
     * contradict each other.
     */
    static Integer readingDays(Object startedAt, Object finishedAt) {
        java.time.LocalDate start = toDate(startedAt);
        java.time.LocalDate end = toDate(finishedAt);
        if (start == null || end == null || end.isBefore(start)) {
            return null;
        }
        return Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1);
    }

    private static java.time.LocalDate toDate(Object value) {
        if (value instanceof java.time.LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        return null;
    }

    private static Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static Double toDouble(Object value) {
        return value == null ? null : ((Number) value).doubleValue();
    }
}
