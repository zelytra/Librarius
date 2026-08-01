package zelytra.librarius.social;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import zelytra.librarius.domain.repository.AppUserRepository;
import zelytra.librarius.domain.repository.UserBlockRepository;
import zelytra.librarius.web.ApiDtos.MemberSummaryDto;

import java.util.List;

/**
 * Blocking another member (#203), building on the member follow (#200). A block stops the two
 * accounts from seeing each other's shared content and from following one another, and is
 * scoped by the caller's own id — a block is always issued as the caller, and the blocked list
 * only ever reads the caller's own edges.
 *
 * <p>The block is stored one-directionally (blocker → blocked) but takes effect both ways: the
 * reusable predicate {@link UserBlockRepository#isBlockBetween} is what the visibility gate
 * (#201) and later the feed and reviews read it through. Existing follows are left as-is —
 * unblocking restores whatever the follow rules would otherwise grant.
 */
@ApplicationScoped
public class UserBlockService {

    @Inject
    UserBlockRepository blocks;

    @Inject
    AppUserRepository users;

    /**
     * The caller starts blocking {@code targetId}. Idempotent.
     *
     * @throws BadRequestException when a user tries to block themselves
     * @throws NotFoundException   when no such user exists — an unknown id is a 404, never a
     *                             403 that would confirm the account is simply out of reach
     */
    @Transactional
    public void block(String blockerId, String targetId) {
        requireDistinctExistingTarget(blockerId, targetId);
        blocks.block(blockerId, targetId);
    }

    /**
     * The caller stops blocking {@code targetId}. Idempotent.
     *
     * @throws BadRequestException when a user targets themselves
     * @throws NotFoundException   when no such user exists
     */
    @Transactional
    public void unblock(String blockerId, String targetId) {
        requireDistinctExistingTarget(blockerId, targetId);
        blocks.unblock(blockerId, targetId);
    }

    /** The members the caller blocks. Relationship metadata about the caller's own account. */
    public List<MemberSummaryDto> blocked(String userId) {
        return blocks.blocked(userId).stream().map(MemberSummaryDto::of).toList();
    }

    private void requireDistinctExistingTarget(String blockerId, String targetId) {
        if (targetId.equals(blockerId)) {
            throw new BadRequestException("A user cannot block themselves.");
        }
        if (users.findById(targetId) == null) {
            throw new NotFoundException();
        }
    }
}
