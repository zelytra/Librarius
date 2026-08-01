package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.SeriesReview;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class SeriesReviewRepository implements PanacheRepositoryBase<SeriesReview, UUID> {

    /** The caller's own review of a series, empty when they have not written one. */
    public Optional<SeriesReview> findOwn(String userId, UUID seriesId) {
        return find("userId = ?1 and seriesId = ?2", userId, seriesId).firstResultOptional();
    }

    /** Removes the caller's own review. Idempotent, like every {@code *_follow} delete. */
    public void deleteOwn(String userId, UUID seriesId) {
        delete("userId = ?1 and seriesId = ?2", userId, seriesId);
    }
}
