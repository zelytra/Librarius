package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.domain.UserFollow;

import java.util.List;

/**
 * The user-to-user follow edges (#200). Every method is scoped by an explicit user id: a
 * follower id for what the caller follows, a followee id for who follows the caller.
 */
@ApplicationScoped
public class UserFollowRepository
        implements PanacheRepositoryBase<UserFollow, UserFollow.Key> {

    public boolean isFollowing(String followerId, String followeeId) {
        return count("followerId = ?1 and followeeId = ?2", followerId, followeeId) > 0;
    }

    /**
     * Starts following another user. Idempotent: a {@code PUT} repeated by a client that lost
     * the response must not fail on the primary key.
     */
    public void follow(String followerId, String followeeId) {
        if (isFollowing(followerId, followeeId)) {
            return;
        }
        UserFollow follow = new UserFollow();
        follow.followerId = followerId;
        follow.followeeId = followeeId;
        persist(follow);
    }

    /** Stops following another user. Idempotent as well. */
    public void unfollow(String followerId, String followeeId) {
        delete("followerId = ?1 and followeeId = ?2", followerId, followeeId);
    }

    /** The accounts this user follows, most recent first. */
    public List<AppUser> following(String userId) {
        return getEntityManager()
                .createQuery("select u from UserFollow f join AppUser u on u.id = f.followeeId "
                        + "where f.followerId = :id order by f.createdAt desc", AppUser.class)
                .setParameter("id", userId)
                .getResultList();
    }

    /** The accounts that follow this user, most recent first. */
    public List<AppUser> followers(String userId) {
        return getEntityManager()
                .createQuery("select u from UserFollow f join AppUser u on u.id = f.followerId "
                        + "where f.followeeId = :id order by f.createdAt desc", AppUser.class)
                .setParameter("id", userId)
                .getResultList();
    }
}
