package zelytra.librarius.social;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import zelytra.librarius.domain.repository.AppUserRepository;
import zelytra.librarius.domain.repository.UserBlockRepository;
import zelytra.librarius.domain.repository.UserFollowRepository;
import zelytra.librarius.web.ApiDtos.MemberSummaryDto;

import java.util.List;

/**
 * The user-to-user follow relationship (#200): who follows whom, and the lists a caller reads
 * of their own account. Everything here is scoped by the caller's own id — a follow is
 * always issued as the caller, and the lists only ever read the caller's own edges.
 *
 * <p>This is the social follow, distinct from the series and author follows: those link a
 * user to a catalog entity, this links a user to another user. What following <em>unlocks</em>
 * — the mutual-follow visibility gate — is #201 and deliberately not here.
 */
@ApplicationScoped
public class UserFollowService {

    @Inject
    UserFollowRepository follows;

    @Inject
    UserBlockRepository blocks;

    @Inject
    AppUserRepository users;

    /**
     * The caller starts following {@code targetId}. Idempotent.
     *
     * @throws BadRequestException when a user tries to follow themselves, or when a block
     *                             stands between the two accounts in either direction — a
     *                             block overrides a follow (#203), so neither the blocker nor
     *                             the blocked party can open a new follow while it stands
     * @throws NotFoundException   when no such user exists — an unknown id is a 404, never a
     *                             403 that would confirm the account is simply out of reach
     */
    @Transactional
    public void follow(String followerId, String targetId) {
        requireDistinctExistingTarget(followerId, targetId);
        if (blocks.isBlockBetween(followerId, targetId)) {
            throw new BadRequestException("A block stands between the two accounts.");
        }
        follows.follow(followerId, targetId);
    }

    /**
     * The caller stops following {@code targetId}. Idempotent.
     *
     * @throws BadRequestException when a user targets themselves
     * @throws NotFoundException   when no such user exists
     */
    @Transactional
    public void unfollow(String followerId, String targetId) {
        requireDistinctExistingTarget(followerId, targetId);
        follows.unfollow(followerId, targetId);
    }

    /** Who the caller follows. Relationship metadata about the caller's own account. */
    public List<MemberSummaryDto> following(String userId) {
        return follows.following(userId).stream().map(MemberSummaryDto::of).toList();
    }

    /** Who follows the caller. Relationship metadata about the caller's own account. */
    public List<MemberSummaryDto> followers(String userId) {
        return follows.followers(userId).stream().map(MemberSummaryDto::of).toList();
    }

    private void requireDistinctExistingTarget(String followerId, String targetId) {
        if (targetId.equals(followerId)) {
            throw new BadRequestException("A user cannot follow themselves.");
        }
        if (users.findById(targetId) == null) {
            throw new NotFoundException();
        }
    }
}
