package zelytra.librarius.social;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import zelytra.librarius.web.ApiDtos.SharedReviewDto;
import zelytra.librarius.web.ApiDtos.SharedReviewsDto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The reviews readers shared on a work or a series (#206), as one caller may see them.
 *
 * <p>Only rows marked shared (#205) are ever read: a private rating never moves an average nor
 * appears in a list. Among those, each author goes through {@link VisibilityGate} — the caller
 * sees a review when they could already see its author's activity, and never across a block,
 * in either direction (#208). The average is computed over exactly the reviews returned, so a
 * hidden review cannot be inferred from it.
 */
@ApplicationScoped
public class SharedReviewService {

    /** Reviews listed at most: the newest first. */
    static final int LIMIT = 50;

    @Inject
    EntityManager em;

    @Inject
    VisibilityGate gate;

    @Transactional
    public SharedReviewsDto ofWork(String viewerId, UUID workId) {
        List<Object[]> rows = em.createQuery("""
                        select u.id, u.displayName, li.rating, li.review, li.createdAt, li.id
                        from LibraryItem li, AppUser u
                        where u.id = li.userId
                          and li.edition.work.id = :work
                          and li.reviewShared = true
                          and (li.rating is not null or li.review is not null)
                        order by li.createdAt desc""", Object[].class)
                .setParameter("work", workId)
                .getResultList();
        return visible(viewerId, rows);
    }

    @Transactional
    public SharedReviewsDto ofSeries(String viewerId, UUID seriesId) {
        List<Object[]> rows = em.createQuery("""
                        select u.id, u.displayName, r.rating, r.review, r.updatedAt, r.id
                        from SeriesReview r, AppUser u
                        where u.id = r.userId
                          and r.seriesId = :series
                          and r.shared = true
                        order by r.updatedAt desc""", Object[].class)
                .setParameter("series", seriesId)
                .getResultList();
        return visible(viewerId, rows);
    }

    private SharedReviewsDto visible(String viewerId, List<Object[]> rows) {
        List<SharedReviewDto> reviews = rows.stream()
                .filter(row -> gate.canView(viewerId, (String) row[0]))
                .limit(LIMIT)
                .map(row -> new SharedReviewDto((UUID) row[5], (String) row[0], (String) row[1],
                        (Integer) row[2], (String) row[3], (OffsetDateTime) row[4]))
                .toList();
        List<Integer> ratings = reviews.stream().map(SharedReviewDto::rating).filter(r -> r != null).toList();
        Double average = ratings.isEmpty() ? null
                : Math.round(ratings.stream().mapToInt(Integer::intValue).average().orElse(0) * 10) / 10.0;
        return new SharedReviewsDto(average, reviews.size(), reviews);
    }
}
