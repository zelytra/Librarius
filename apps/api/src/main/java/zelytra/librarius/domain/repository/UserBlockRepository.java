package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.domain.UserBlock;

import java.util.List;

/**
 * The user-to-user block edges (#203). A block is stored one-directionally but read
 * symmetrically: {@link #isBlockBetween(String, String)} is the reusable predicate the
 * visibility gate (#201) and later the feed and reviews consult before revealing one member's
 * content to another.
 */
@ApplicationScoped
public class UserBlockRepository
        implements PanacheRepositoryBase<UserBlock, UserBlock.Key> {

    /** Whether {@code blockerId} currently blocks {@code blockedId} (one direction only). */
    public boolean isBlocking(String blockerId, String blockedId) {
        return count("blockerId = ?1 and blockedId = ?2", blockerId, blockedId) > 0;
    }

    /**
     * Whether a block stands between the two accounts, in <em>either</em> direction. This is
     * the predicate a block is meant to be read through: a block hides content both ways, so
     * whoever gates one member's content on another asks this, never {@link #isBlocking} — the
     * blocked party is as cut off from the blocker's content as the reverse.
     */
    public boolean isBlockBetween(String a, String b) {
        return count("(blockerId = ?1 and blockedId = ?2) or (blockerId = ?2 and blockedId = ?1)",
                a, b) > 0;
    }

    /**
     * Starts blocking another member. Idempotent: a {@code PUT} repeated by a client that lost
     * the response must not fail on the primary key.
     */
    public void block(String blockerId, String blockedId) {
        if (isBlocking(blockerId, blockedId)) {
            return;
        }
        UserBlock block = new UserBlock();
        block.blockerId = blockerId;
        block.blockedId = blockedId;
        persist(block);
    }

    /** Stops blocking another member. Idempotent as well. */
    public void unblock(String blockerId, String blockedId) {
        delete("blockerId = ?1 and blockedId = ?2", blockerId, blockedId);
    }

    /** The accounts this user blocks, most recent first. */
    public List<AppUser> blocked(String userId) {
        return getEntityManager()
                .createQuery("select u from UserBlock b join AppUser u on u.id = b.blockedId "
                        + "where b.blockerId = :id order by b.createdAt desc", AppUser.class)
                .setParameter("id", userId)
                .getResultList();
    }
}
