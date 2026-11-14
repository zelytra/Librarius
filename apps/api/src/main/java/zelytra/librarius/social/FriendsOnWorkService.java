package zelytra.librarius.social;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import zelytra.librarius.domain.Work;
import zelytra.librarius.web.ApiDtos.FriendOnWorkDto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The people a reader follows who have a title too (#452): who read it, own it or want it,
 * with the category they filed it under and — when they shared it — their rating.
 *
 * <p>Only people the caller follows are listed, and each one goes through
 * {@link VisibilityGate} like every cross-account read: a private account shows nothing
 * without a follow back, and nothing crosses a block. A rating stays private unless its
 * owner shared it (#205), exactly as on the reviews list.
 *
 * <p>The same book can sit on two works — a title one reader filed as a manga and another as
 * a book (#454) — so a copy counts when it is on this work or on a work with the same title
 * and authors.
 */
@ApplicationScoped
public class FriendsOnWorkService {

    /** Friends listed at most. */
    static final int LIMIT = 50;

    @Inject
    EntityManager em;

    @Inject
    VisibilityGate gate;

    @Transactional
    public List<FriendOnWorkDto> of(String viewerId, UUID workId) {
        Work work = em.find(Work.class, workId);
        if (work == null) {
            return List.of();
        }
        String sameBook = """
                (w.id = :work or (lower(w.title) = lower(:title)
                  and coalesce(lower(w.authorsText), '') = coalesce(lower(:authors), '')))""";
        List<Object[]> held = em.createQuery("""
                        select u.id, u.displayName, u.trusted, li.status, li.ownership, li.rating,
                               li.reviewShared, r.code, r.label, r.color
                        from LibraryItem li join li.edition e join e.work w
                             left join li.rankCategory r, AppUser u, UserFollow f
                        where u.id = li.userId and f.followerId = :viewer and f.followeeId = li.userId
                          and %s
                        order by u.displayName""".formatted(sameBook), Object[].class)
                .setParameter("viewer", viewerId)
                .setParameter("work", workId)
                .setParameter("title", work.title)
                .setParameter("authors", work.authorsText)
                .getResultList();
        List<Object[]> wished = em.createQuery("""
                        select u.id, u.displayName, u.trusted
                        from WishlistItem wi join wi.edition e join e.work w, AppUser u, UserFollow f
                        where u.id = wi.userId and f.followerId = :viewer and f.followeeId = wi.userId
                          and %s
                        order by u.displayName""".formatted(sameBook), Object[].class)
                .setParameter("viewer", viewerId)
                .setParameter("work", workId)
                .setParameter("title", work.title)
                .setParameter("authors", work.authorsText)
                .getResultList();

        Map<String, FriendOnWorkDto> friends = new LinkedHashMap<>();
        for (Object[] row : held) {
            String id = (String) row[0];
            if (friends.containsKey(id) || !gate.canView(viewerId, id)) {
                continue;
            }
            boolean shared = Boolean.TRUE.equals(row[6]);
            friends.put(id, new FriendOnWorkDto(id, (String) row[1], (Boolean) row[2],
                    String.valueOf(row[3]), String.valueOf(row[4]), false,
                    shared ? (Integer) row[5] : null, (String) row[7], (String) row[8], (String) row[9]));
        }
        for (Object[] row : wished) {
            String id = (String) row[0];
            if (!gate.canView(viewerId, id)) {
                continue;
            }
            FriendOnWorkDto known = friends.get(id);
            friends.put(id, known != null ? known.withWish()
                    : new FriendOnWorkDto(id, (String) row[1], (Boolean) row[2], null, null, true,
                            null, null, null, null));
        }
        return new ArrayList<>(friends.values()).subList(0, Math.min(LIMIT, friends.size()));
    }
}
