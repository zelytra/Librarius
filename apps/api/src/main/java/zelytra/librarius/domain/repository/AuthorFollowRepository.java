package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.AuthorFollow;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The authors a user follows. Every query is scoped by {@code user_id}: a follow is private,
 * and no caller ever reads another account's.
 */
@ApplicationScoped
public class AuthorFollowRepository
        implements PanacheRepositoryBase<AuthorFollow, AuthorFollow.Key> {

    /** Identifiers of the authors the user follows, for flagging a list in one query. */
    public Set<UUID> followedAuthorIds(String userId) {
        return getEntityManager()
                .createQuery("select f.authorId from AuthorFollow f where f.userId = :userId",
                        UUID.class)
                .setParameter("userId", userId)
                .getResultList()
                .stream()
                .collect(Collectors.toSet());
    }

    public boolean isFollowing(String userId, UUID authorId) {
        return count("userId = ?1 and authorId = ?2", userId, authorId) > 0;
    }

    /**
     * Starts following an author. Idempotent: a {@code PUT} repeated by a client that lost
     * the response must not fail on the primary key.
     */
    public void follow(String userId, UUID authorId) {
        if (isFollowing(userId, authorId)) {
            return;
        }
        AuthorFollow follow = new AuthorFollow();
        follow.userId = userId;
        follow.authorId = authorId;
        persist(follow);
    }

    /** Stops following an author. Idempotent as well. */
    public void unfollow(String userId, UUID authorId) {
        delete("userId = ?1 and authorId = ?2", userId, authorId);
    }
}
