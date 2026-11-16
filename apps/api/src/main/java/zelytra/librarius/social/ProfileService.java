package zelytra.librarius.social;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.domain.repository.AppUserRepository;
import zelytra.librarius.domain.repository.LibraryItemRepository;
import zelytra.librarius.domain.repository.LibraryItemRepository.GenreTotal;
import zelytra.librarius.domain.repository.LibraryItemRepository.StatusTotals;
import zelytra.librarius.domain.repository.UserBlockRepository;
import zelytra.librarius.domain.repository.UserFollowRepository;
import zelytra.librarius.web.ApiDtos.MemberProfileDto;
import zelytra.librarius.web.ApiDtos.ProfileReadingDto;
import zelytra.librarius.web.ApiDtos.ProfileTitleDto;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * A member's profile page (#497), as one caller sees it.
 *
 * <p>Two layers, the same two #201 draws everywhere: who the member is — display name, trusted
 * badge, how the two accounts relate, the follow counts — is visible to anyone signed in; what
 * they read is visible only when {@link VisibilityGate#canView} says so. A block, in either
 * direction, or an id that is nobody, answers empty — the resource turns both into the same 404,
 * so a profile confirms nothing about an account out of reach. A rating is shown only when its
 * owner shared it (#205); a private review never appears.
 */
@ApplicationScoped
public class ProfileService {

    static final int CURRENT_LIMIT = 6;
    static final int FINISHED_LIMIT = 8;
    static final int REVIEWS_LIMIT = 10;
    static final int GENRES_LIMIT = 5;

    @Inject
    EntityManager em;

    @Inject
    AppUserRepository users;

    @Inject
    UserFollowRepository follows;

    @Inject
    UserBlockRepository blocks;

    @Inject
    LibraryItemRepository items;

    @Inject
    VisibilityGate gate;

    @Transactional
    public Optional<MemberProfileDto> of(String viewerId, String memberId) {
        AppUser member = users.findById(memberId);
        if (member == null || blocks.isBlockBetween(viewerId, memberId)) {
            return Optional.empty();
        }
        boolean self = viewerId.equals(memberId);
        boolean visible = gate.canView(viewerId, memberId);
        return Optional.of(new MemberProfileDto(member.id, member.displayName, member.trusted,
                member.createdAt, self,
                !self && follows.isFollowing(viewerId, memberId),
                !self && follows.isFollowing(memberId, viewerId),
                count("select count(f) from UserFollow f where f.followeeId = :id", memberId),
                count("select count(f) from UserFollow f where f.followerId = :id", memberId),
                visible, visible ? reading(memberId) : null));
    }

    private ProfileReadingDto reading(String memberId) {
        StatusTotals totals = items.statusTotals(memberId);
        LocalDate yearStart = LocalDate.now().withDayOfYear(1);
        long readThisYear = em.createQuery("""
                        select count(li) from LibraryItem li join li.progress p
                        where li.userId = :id and li.status = :read and p.finishedAt >= :from""", Long.class)
                .setParameter("id", memberId)
                .setParameter("read", LibraryStatus.READ)
                .setParameter("from", yearStart)
                .getSingleResult();
        List<String> genres = items.topGenres(memberId, GENRES_LIMIT).stream().map(GenreTotal::label).toList();

        List<ProfileTitleDto> current = em.createQuery("""
                        select li from LibraryItem li left join fetch li.progress p
                        join fetch li.edition e join fetch e.work
                        where li.userId = :id and li.status = :reading
                        order by p.startedAt desc nulls last, li.createdAt desc""", LibraryItem.class)
                .setParameter("id", memberId)
                .setParameter("reading", LibraryStatus.READING)
                .setMaxResults(CURRENT_LIMIT)
                .getResultStream().map(li -> title(li, false)).toList();
        List<ProfileTitleDto> finished = em.createQuery("""
                        select li from LibraryItem li join fetch li.progress p
                        join fetch li.edition e join fetch e.work
                        where li.userId = :id and li.status = :read and p.finishedAt is not null
                        order by p.finishedAt desc, li.createdAt desc""", LibraryItem.class)
                .setParameter("id", memberId)
                .setParameter("read", LibraryStatus.READ)
                .setMaxResults(FINISHED_LIMIT)
                .getResultStream().map(li -> title(li, false)).toList();
        List<ProfileTitleDto> reviews = em.createQuery("""
                        select li from LibraryItem li left join fetch li.progress
                        join fetch li.edition e join fetch e.work
                        where li.userId = :id and li.reviewShared = true
                          and (li.rating is not null or li.review is not null)
                        order by li.createdAt desc""", LibraryItem.class)
                .setParameter("id", memberId)
                .setMaxResults(REVIEWS_LIMIT)
                .getResultStream().map(li -> title(li, true)).toList();

        return new ProfileReadingDto(totals.read(), totals.reading(), totals.toRead(), totals.abandoned(),
                count("select count(w) from WishlistItem w where w.userId = :id", memberId),
                totals.pagesRead(), readThisYear, genres, current, finished, reviews);
    }

    private static ProfileTitleDto title(LibraryItem li, boolean withReview) {
        var e = li.edition;
        var w = e.work;
        var p = li.progress;
        return new ProfileTitleDto(e.id, w.id, w.kind.name(), w.title, w.authorsText, e.coverUrl,
                li.reviewShared ? li.rating : null, withReview && li.reviewShared ? li.review : null,
                p != null ? p.finishedAt : null, p != null ? p.percent : null);
    }

    private long count(String jpql, String id) {
        return em.createQuery(jpql, Long.class).setParameter("id", id).getSingleResult();
    }
}
