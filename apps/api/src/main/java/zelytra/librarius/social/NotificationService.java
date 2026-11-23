package zelytra.librarius.social;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.repository.LibraryItemRepository;
import zelytra.librarius.web.ApiDtos.MemberBookDto;
import zelytra.librarius.web.ApiDtos.MemberSummaryDto;
import zelytra.librarius.web.ApiDtos.NotificationDto;
import zelytra.librarius.web.ApiDtos.NotificationEditDto;
import zelytra.librarius.web.ApiDtos.NotificationPageDto;
import zelytra.librarius.web.PageRequest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The bell (#516) and the comment notifications of #211, in the app.
 *
 * <p>A notification is written where the thing happens, for one recipient:
 * <ul>
 *   <li>{@code ACTIVITY} — a feed event was logged: every follower of its author whose follow
 *       still notifies (on by default, switched off on the author's profile) and who may see the
 *       author ({@link VisibilityGate}, the feed's own rule);</li>
 *   <li>{@code REACTION} — someone reacted to my event (a changed reaction replaces the
 *       notification rather than stacking a second);</li>
 *   <li>{@code COMMENT} — someone commented on my event, unless I switched comments off;</li>
 *   <li>{@code FOLLOW} — someone started following me (#544). It is about them, not about an
 *       event: it carries none, and a follow after an unfollow replaces the earlier one.</li>
 *   <li>{@code EDIT} — an admin approved, rejected or reverted my catalog edit proposal (#618).</li>
 * </ul>
 * Nobody is notified of their own doing, and nothing crosses a block: a notification whose actor
 * a block now stands between with the recipient is not shown.
 */
@ApplicationScoped
public class NotificationService {

    public enum Type { ACTIVITY, REACTION, COMMENT, FOLLOW, EDIT }

    @Inject
    EntityManager em;

    @Inject
    VisibilityGate gate;

    @Inject
    LibraryItemRepository libraryItems;

    /** A feed event was just logged by {@code actorId}: their notified followers hear of it. */
    public void activity(String actorId, UUID eventId) {
        @SuppressWarnings("unchecked")
        List<String> followers = em.createNativeQuery(
                        "select follower_id from user_follow where followee_id = :actor and notify")
                .setParameter("actor", actorId)
                .getResultList();
        for (String follower : followers) {
            if (gate.canView(follower, actorId)) {
                insert(follower, Type.ACTIVITY, actorId, eventId, null, null);
            }
        }
    }

    /** {@code actorId} reacted to an event: its author hears of it, once per reactor. */
    public void reaction(String actorId, UUID eventId, String emote) {
        String owner = ownerOf(eventId);
        if (owner == null || owner.equals(actorId)) {
            return;
        }
        em.createNativeQuery("""
                        delete from notification
                        where user_id = :owner and actor_id = :actor and feed_event_id = :event and type = 'REACTION'""")
                .setParameter("owner", owner)
                .setParameter("actor", actorId)
                .setParameter("event", eventId)
                .executeUpdate();
        insert(owner, Type.REACTION, actorId, eventId, null, emote);
    }

    /** {@code actorId} commented on an event: its author hears of it unless they turned that off. */
    public void comment(String actorId, UUID eventId, UUID commentId) {
        String owner = ownerOf(eventId);
        if (owner == null || owner.equals(actorId)) {
            return;
        }
        AppUser recipient = em.find(AppUser.class, owner);
        if (recipient == null || !recipient.notifyComments) {
            return;
        }
        insert(owner, Type.COMMENT, actorId, eventId, commentId, null);
    }

    /** {@code actorId} started following {@code recipientId}: they hear of it, once per follower. */
    public void follow(String actorId, String recipientId) {
        em.createNativeQuery("""
                        delete from notification
                        where user_id = :recipient and actor_id = :actor and type = 'FOLLOW'""")
                .setParameter("recipient", recipientId)
                .setParameter("actor", actorId)
                .executeUpdate();
        insert(recipientId, Type.FOLLOW, actorId, null, null, null);
    }

    /** {@code adminId} decided on {@code recipientId}'s catalog edit proposal {@code editId}. */
    public void edit(String adminId, String recipientId, UUID editId) {
        if (recipientId == null || recipientId.equals(adminId)) {
            return;
        }
        em.createNativeQuery("""
                        insert into notification (id, user_id, type, actor_id, catalog_edit_id, created_at)
                        values (:id, :user, 'EDIT', :actor, :edit, now())""")
                .setParameter("id", UUID.randomUUID())
                .setParameter("user", recipientId)
                .setParameter("actor", adminId)
                .setParameter("edit", editId)
                .executeUpdate();
    }

    @Transactional
    public NotificationPageDto list(String userId, PageRequest window) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        select n.id, n.type, n.feed_event_id, fe.type as event_type, fe.edition_id, n.emote,
                               c.comment, n.created_at, n.read_at,
                               u.id as uid, u.display_name, u.trusted, u.avatar_version,
                               exists (select 1 from user_follow f
                                       where f.follower_id = n.user_id and f.followee_id = n.actor_id) as following_actor,
                               ce.id as edit_id, ce.status as edit_status, ce.target_type, ce.target_id,
                               coalesce(w.title, s.title) as edit_title, ce.review_note, w.id as edit_work
                        from notification n
                          join app_user u on u.id = n.actor_id
                          left join feed_event fe on fe.id = n.feed_event_id
                          left join feed_comment c on c.id = n.comment_id
                          left join catalog_edit ce on ce.id = n.catalog_edit_id
                          left join edition ed on ce.target_type = 'EDITION' and ed.id = ce.target_id
                          left join work w on w.id = ed.work_id
                          left join series s on ce.target_type = 'SERIES' and s.id = ce.target_id
                        where n.user_id = :user""" + NOT_BLOCKED + """
                        order by n.created_at desc, n.id""")
                .setParameter("user", userId)
                .setFirstResult(window.offset())
                .setMaxResults(window.size() + 1)
                .getResultList();
        boolean hasMore = rows.size() > window.size();
        List<Object[]> page = hasMore ? rows.subList(0, window.size()) : rows;
        // A follow notification is about no event, hence no book.
        List<Edition> editions = page.stream()
                .map(row -> row[4] == null ? null : em.find(Edition.class, row[4])).toList();
        List<UUID> works = new ArrayList<>(editions.stream().filter(java.util.Objects::nonNull)
                .map(e -> e.work.id).toList());
        page.stream().map(row -> (UUID) row[20]).filter(java.util.Objects::nonNull).forEach(works::add);
        Map<UUID, UUID> mine = libraryItems.itemIdsByWork(userId, works.stream().distinct().toList());
        List<NotificationDto> items = new ArrayList<>();
        for (int i = 0; i < page.size(); i++) {
            Object[] row = page.get(i);
            Edition edition = editions.get(i);
            items.add(new NotificationDto((UUID) row[0], (String) row[1],
                    new MemberSummaryDto((String) row[9], (String) row[10], Boolean.TRUE.equals(row[11]),
                            row[12] == null ? null : ((Number) row[12]).longValue()),
                    (UUID) row[2], (String) row[3],
                    edition == null ? null : MemberBookDto.of(edition, mine.get(edition.work.id)),
                    (String) row[6], (String) row[5], toOffset(row[7]), toOffset(row[8]),
                    Boolean.TRUE.equals(row[13]),
                    row[14] == null ? null : new NotificationEditDto((UUID) row[14], (String) row[15],
                            (String) row[16], (UUID) row[17], (String) row[18], (String) row[19],
                            row[20] == null ? null : mine.get((UUID) row[20]))));
        }
        return new NotificationPageDto(items, window.page(), window.size(), hasMore);
    }

    @Transactional
    public long unread(String userId) {
        return ((Number) em.createNativeQuery("select count(*) from notification n where n.user_id = :user"
                        + " and n.read_at is null" + NOT_BLOCKED)
                .setParameter("user", userId)
                .getSingleResult()).longValue();
    }

    @Transactional
    public void readAll(String userId) {
        em.createNativeQuery("update notification set read_at = now() where user_id = :user and read_at is null")
                .setParameter("user", userId)
                .executeUpdate();
    }

    /** Switches a follow's notifications; false when the caller does not follow the member. */
    @Transactional
    public boolean setFollowNotify(String followerId, String followeeId, boolean notify) {
        return em.createNativeQuery(
                        "update user_follow set notify = :notify where follower_id = :follower and followee_id = :followee")
                .setParameter("notify", notify)
                .setParameter("follower", followerId)
                .setParameter("followee", followeeId)
                .executeUpdate() > 0;
    }

    /** Leaves out a notification whose actor a block now stands between with the recipient. */
    private static final String NOT_BLOCKED = """

             and not exists (select 1 from user_block b
                             where (b.blocker_id = n.user_id and b.blocked_id = n.actor_id)
                                or (b.blocker_id = n.actor_id and b.blocked_id = n.user_id))
            """;

    private void insert(String userId, Type type, String actorId, UUID eventId, UUID commentId, String emote) {
        em.createNativeQuery("""
                        insert into notification (id, user_id, type, actor_id, feed_event_id, comment_id, emote, created_at)
                        values (:id, :user, :type, :actor, cast(:event as uuid), cast(:comment as uuid), cast(:emote as varchar), now())""")
                .setParameter("id", UUID.randomUUID())
                .setParameter("user", userId)
                .setParameter("type", type.name())
                .setParameter("actor", actorId)
                .setParameter("event", eventId)
                .setParameter("comment", commentId)
                .setParameter("emote", emote)
                .executeUpdate();
    }

    private String ownerOf(UUID eventId) {
        @SuppressWarnings("unchecked")
        List<String> rows = em.createNativeQuery("select user_id from feed_event where id = :id")
                .setParameter("id", eventId)
                .getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static OffsetDateTime toOffset(Object value) {
        if (value instanceof OffsetDateTime odt) {
            return odt;
        }
        if (value instanceof java.time.Instant instant) {
            return instant.atOffset(ZoneOffset.UTC);
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atOffset(ZoneOffset.UTC);
        }
        return null;
    }
}
