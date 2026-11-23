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
import zelytra.librarius.web.ApiDtos.MemberBookDto;
import zelytra.librarius.web.ApiDtos.MemberShelfPageDto;

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
        return Optional.of(new MemberProfileDto(member.id, member.displayName, member.trusted, member.avatarVersion,
                member.createdAt, self,
                !self && follows.isFollowing(viewerId, memberId),
                !self && follows.isFollowing(memberId, viewerId),
                count("select count(f) from UserFollow f where f.followeeId = :id", memberId),
                count("select count(f) from UserFollow f where f.followerId = :id", memberId),
                visible, visible ? reading(viewerId, memberId) : null, followNotify(viewerId, memberId),
                member.admin));
    }

    /** Whether the caller's follow of the member notifies them (#516); null when they do not follow. */
    private Boolean followNotify(String viewerId, String memberId) {
        @SuppressWarnings("unchecked")
        List<Boolean> rows = em.createNativeQuery(
                        "select notify from user_follow where follower_id = :viewer and followee_id = :member")
                .setParameter("viewer", viewerId)
                .setParameter("member", memberId)
                .getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    private ProfileReadingDto reading(String viewerId, String memberId) {
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

        List<LibraryItem> current = em.createQuery("""
                        select li from LibraryItem li left join fetch li.progress p
                        join fetch li.edition e join fetch e.work
                        where li.userId = :id and li.status = :reading
                        order by p.startedAt desc nulls last, li.createdAt desc""", LibraryItem.class)
                .setParameter("id", memberId)
                .setParameter("reading", LibraryStatus.READING)
                .setMaxResults(CURRENT_LIMIT)
                .getResultList();
        List<LibraryItem> finished = em.createQuery("""
                        select li from LibraryItem li join fetch li.progress p
                        join fetch li.edition e join fetch e.work
                        where li.userId = :id and li.status = :read and p.finishedAt is not null
                        order by p.finishedAt desc, li.createdAt desc""", LibraryItem.class)
                .setParameter("id", memberId)
                .setParameter("read", LibraryStatus.READ)
                .setMaxResults(FINISHED_LIMIT)
                .getResultList();
        List<LibraryItem> reviews = em.createQuery("""
                        select li from LibraryItem li left join fetch li.progress
                        join fetch li.edition e join fetch e.work
                        where li.userId = :id and li.reviewShared = true
                          and (li.rating is not null or li.review is not null)
                        order by li.createdAt desc""", LibraryItem.class)
                .setParameter("id", memberId)
                .setMaxResults(REVIEWS_LIMIT)
                .getResultList();

        List<LibraryItem> shown = new java.util.ArrayList<>(current);
        shown.addAll(finished);
        shown.addAll(reviews);
        var mine = mine(viewerId, shown);
        return new ProfileReadingDto(totals.read(), totals.reading(), totals.toRead(), totals.abandoned(),
                count("select count(w) from WishlistItem w where w.userId = :id", memberId),
                totals.pagesRead(), readThisYear, genres,
                current.stream().map(li -> title(li, false, mine)).toList(),
                finished.stream().map(li -> title(li, false, mine)).toList(),
                reviews.stream().map(li -> title(li, true, mine)).toList());
    }

    private ProfileTitleDto title(LibraryItem li, boolean withReview, java.util.Map<java.util.UUID, java.util.UUID> mine) {
        var e = li.edition;
        var p = li.progress;
        return new ProfileTitleDto(MemberBookDto.of(e, mine.get(e.work.id)), li.status.name(),
                li.reviewShared ? li.rating : null, withReview && li.reviewShared ? li.review : null,
                p != null ? p.finishedAt : null, p != null ? p.percent : null);
    }

    /** The viewer's own copies of the works of {@code items}, keyed by work (#513). */
    private java.util.Map<java.util.UUID, java.util.UUID> mine(String viewerId, List<LibraryItem> items) {
        return items(viewerId, items.stream().map(li -> li.edition.work.id).distinct().toList());
    }

    private java.util.Map<java.util.UUID, java.util.UUID> items(String viewerId, List<java.util.UUID> workIds) {
        return this.items.itemIdsByWork(viewerId, workIds);
    }

    public enum Shelf { LIBRARY, TO_READ, WISHLIST }

    /**
     * One shelf of a member (#514) — the whole library, the to-read pile or the wishlist — a page
     * at a time, by title; {@code query} narrows on the title. Empty when the viewer may not see
     * the member, which the resource turns into 404.
     */
    @Transactional
    public Optional<MemberShelfPageDto> shelf(String viewerId, String memberId, Shelf shelf,
            zelytra.librarius.web.PageRequest window, String query) {
        if (users.findById(memberId) == null || !gate.canView(viewerId, memberId)) {
            return Optional.empty();
        }
        String like = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
        String titleFilter = like == null ? "" : " and lower(w.title) like :q";
        if (shelf == Shelf.WISHLIST) {
            var rows = em.createQuery("select wi from WishlistItem wi join fetch wi.edition e join fetch e.work w"
                            + " where wi.userId = :id" + titleFilter + " order by lower(w.title), wi.id",
                    zelytra.librarius.domain.WishlistItem.class).setParameter("id", memberId);
            var count = em.createQuery("select count(wi) from WishlistItem wi join wi.edition e join e.work w"
                    + " where wi.userId = :id" + titleFilter, Long.class).setParameter("id", memberId);
            if (like != null) {
                rows.setParameter("q", like);
                count.setParameter("q", like);
            }
            var wishes = rows.setFirstResult(window.offset()).setMaxResults(window.size()).getResultList();
            var mine = items(viewerId, wishes.stream().map(wi -> wi.edition.work.id).distinct().toList());
            List<ProfileTitleDto> titles = wishes.stream().map(wi -> new ProfileTitleDto(
                    MemberBookDto.of(wi.edition, mine.get(wi.edition.work.id)), null, null, null, null, null)).toList();
            return Optional.of(new MemberShelfPageDto(titles, window.page(), window.size(), count.getSingleResult()));
        }
        String statusFilter = shelf == Shelf.TO_READ ? " and li.status = :status" : "";
        var rows = em.createQuery("select li from LibraryItem li join fetch li.edition e join fetch e.work w"
                        + " left join fetch li.progress where li.userId = :id" + statusFilter + titleFilter
                        + " order by lower(w.title), li.id", LibraryItem.class).setParameter("id", memberId);
        var count = em.createQuery("select count(li) from LibraryItem li join li.edition e join e.work w"
                + " where li.userId = :id" + statusFilter + titleFilter, Long.class).setParameter("id", memberId);
        if (shelf == Shelf.TO_READ) {
            rows.setParameter("status", LibraryStatus.TO_READ);
            count.setParameter("status", LibraryStatus.TO_READ);
        }
        if (like != null) {
            rows.setParameter("q", like);
            count.setParameter("q", like);
        }
        List<LibraryItem> page = rows.setFirstResult(window.offset()).setMaxResults(window.size()).getResultList();
        var mine = mine(viewerId, page);
        return Optional.of(new MemberShelfPageDto(page.stream().map(li -> title(li, false, mine)).toList(),
                window.page(), window.size(), count.getSingleResult()));
    }

    private long count(String jpql, String id) {
        return em.createQuery(jpql, Long.class).setParameter("id", id).getSingleResult();
    }
}
