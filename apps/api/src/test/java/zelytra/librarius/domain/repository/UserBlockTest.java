package zelytra.librarius.domain.repository;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.AppUser;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The block edges (#203) and the reusable predicate the visibility gate (#201) reads them
 * through.
 *
 * <p>A block is stored one-directionally but {@link UserBlockRepository#isBlockBetween} is
 * symmetric: this exercises that a single blocker → blocked row hides content <em>both</em>
 * ways, that removing it returns the pair to whatever the follow rules would otherwise say,
 * and the mandatory isolation — one account's blocks never leak into another's list.
 */
@QuarkusTest
class UserBlockTest {

    @Inject
    UserBlockRepository blocks;

    @Inject
    EntityManager em;

    /** Blocking is idempotent — a repeated PUT must not fail on the primary key. */
    @Test
    void blockingIsIdempotent() {
        String alice = seedUser();
        String bob = seedUser();

        QuarkusTransaction.requiringNew().run(() -> {
            blocks.block(alice, bob);
            blocks.block(alice, bob);
        });

        assertTrue(blocks.isBlocking(alice, bob));
        assertEquals(List.of(bob), blocks.blocked(alice).stream().map(u -> u.id).toList());
    }

    /**
     * The heart of the block: a single alice → bob row makes {@code isBlockBetween} deny in
     * both directions, and removing it clears both — the pair returns to whatever #201's rule
     * would otherwise grant.
     */
    @Test
    void aBlockIsSymmetricAndReversible() {
        String alice = seedUser();
        String bob = seedUser();

        // No block yet: the predicate grants both ways.
        assertFalse(blocks.isBlockBetween(alice, bob));
        assertFalse(blocks.isBlockBetween(bob, alice));

        QuarkusTransaction.requiringNew().run(() -> blocks.block(alice, bob));

        // Stored one way, but the predicate denies both — bob is as cut off from alice as the
        // reverse, even though only alice ever pressed the button.
        assertTrue(blocks.isBlockBetween(alice, bob));
        assertTrue(blocks.isBlockBetween(bob, alice));
        // Only alice's list carries it; bob is never told.
        assertFalse(blocks.isBlocking(bob, alice), "the block is one-directional in storage");

        QuarkusTransaction.requiringNew().run(() -> blocks.unblock(alice, bob));

        // Unblocked: the pair is back to normal both ways, and both accounts survive.
        assertFalse(blocks.isBlockBetween(alice, bob));
        assertFalse(blocks.isBlockBetween(bob, alice));
        assertNotNull(em.find(AppUser.class, alice));
        assertNotNull(em.find(AppUser.class, bob));
    }

    /** The standing rule: one account's blocks never surface in another's list. */
    @Test
    void oneUsersBlocksAreInvisibleToAnother() {
        String alice = seedUser();
        String bob = seedUser();
        String carol = seedUser();

        QuarkusTransaction.requiringNew().run(() -> {
            blocks.block(alice, carol);
            blocks.block(bob, carol);
        });

        assertEquals(List.of(carol), blocks.blocked(alice).stream().map(u -> u.id).toList());
        assertEquals(List.of(carol), blocks.blocked(bob).stream().map(u -> u.id).toList());
        assertEquals(List.of(), blocks.blocked(carol).stream().map(u -> u.id).toList());
        assertFalse(blocks.isBlockBetween(alice, bob), "blocking a common third is not a block "
                + "between the two blockers");
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    /** user_block.blocker_id / blocked_id are foreign keys onto app_user. */
    private String seedUser() {
        String userId = "block-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            AppUser user = new AppUser();
            user.id = userId;
            user.displayName = "Block fixture";
            em.persist(user);
        });
        return userId;
    }
}
