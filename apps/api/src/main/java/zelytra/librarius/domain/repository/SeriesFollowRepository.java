package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.SeriesFollow;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class SeriesFollowRepository
        implements PanacheRepositoryBase<SeriesFollow, SeriesFollow.Key> {

    /** Identifiers of the series the user follows, for flagging a list in one query. */
    public Set<UUID> followedSeriesIds(String userId) {
        return getEntityManager()
                .createQuery("select f.seriesId from SeriesFollow f where f.userId = :userId",
                        UUID.class)
                .setParameter("userId", userId)
                .getResultList()
                .stream()
                .collect(Collectors.toSet());
    }

    public boolean isFollowing(String userId, UUID seriesId) {
        return count("userId = ?1 and seriesId = ?2", userId, seriesId) > 0;
    }

    /**
     * Starts following a series. Idempotent: a {@code PUT} repeated by a client that lost
     * the response must not fail on the primary key.
     */
    public void follow(String userId, UUID seriesId) {
        if (isFollowing(userId, seriesId)) {
            return;
        }
        SeriesFollow follow = new SeriesFollow();
        follow.userId = userId;
        follow.seriesId = seriesId;
        persist(follow);
    }

    /** Stops following a series. Idempotent as well. */
    public void unfollow(String userId, UUID seriesId) {
        delete("userId = ?1 and seriesId = ?2", userId, seriesId);
    }
}
