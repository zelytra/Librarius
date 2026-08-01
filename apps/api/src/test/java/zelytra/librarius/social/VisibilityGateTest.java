package zelytra.librarius.social;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.domain.repository.UserFollowRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mutual-follow visibility gate (#201): the one primitive answering "may this caller see
 * that member's shared content?".
 *
 * <p>The matrix is the whole point — public, mutual, one-way and no relationship must each
 * resolve the one right way, and a bug here is a leak of one account's content to another. So
 * every branch is pinned: the account always sees itself, a public account is open to a
 * stranger, a private account opens only on a mutual follow, and a one-way follow, no follow,
 * an unknown id or a null id all stay closed.
 */
@QuarkusTest
class VisibilityGateTest {

    @Inject
    VisibilityGate gate;

    @Inject
    UserFollowRepository follows;

    @Inject
    EntityManager em;

    /** A caller always sees their own content, whatever the preference or the follow state. */
    @Test
    void anAccountAlwaysSeesItself() {
        String alice = seedUser(false);
        assertTrue(gate.canView(alice, alice));
    }

    /** A public account is visible to a signed-in stranger with zero follow relationship. */
    @Test
    void aPublicAccountIsVisibleToAStranger() {
        String alice = seedUser(false);
        String bob = seedUser(true);

        assertTrue(gate.canView(alice, bob), "bob is public: any signed-in member sees him");
        // ...but bob, private, is not visible to alice in return — public is one account's own
        // choice, not a relationship.
        assertFalse(gate.canView(bob, alice), "alice stayed private: bob cannot see her");
    }

    /** A private account opens to a mutual follow, in both directions. */
    @Test
    void aMutualFollowIsVisibleBothWays() {
        String alice = seedUser(false);
        String bob = seedUser(false);
        QuarkusTransaction.requiringNew().run(() -> {
            follows.follow(alice, bob);
            follows.follow(bob, alice);
        });

        assertTrue(gate.canView(alice, bob));
        assertTrue(gate.canView(bob, alice));
    }

    /** A one-way follow reveals nothing, on either side. */
    @Test
    void aOneWayFollowRevealsNothing() {
        String alice = seedUser(false);
        String bob = seedUser(false);
        // alice follows bob, bob does not follow back.
        QuarkusTransaction.requiringNew().run(() -> follows.follow(alice, bob));

        assertFalse(gate.canView(alice, bob), "the follower does not see the followee back");
        assertFalse(gate.canView(bob, alice), "the followee does not see the follower");
    }

    /** No relationship at all between two private accounts: invisible both ways. */
    @Test
    void noRelationshipIsInvisible() {
        String alice = seedUser(false);
        String bob = seedUser(false);

        assertFalse(gate.canView(alice, bob));
        assertFalse(gate.canView(bob, alice));
    }

    /** An id that is nobody is invisible — the caller cannot tell it apart from "not for you". */
    @Test
    void anUnknownTargetIsInvisible() {
        String alice = seedUser(false);
        assertFalse(gate.canView(alice, UUID.randomUUID().toString()));
    }

    /** A null on either side is invisible, never an exception. */
    @Test
    void aNullIdIsInvisible() {
        String alice = seedUser(false);
        assertFalse(gate.canView(null, alice));
        assertFalse(gate.canView(alice, null));
        assertFalse(gate.canView(null, null));
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private String seedUser(boolean publicAccount) {
        String id = "gate-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            AppUser user = new AppUser();
            user.id = id;
            user.displayName = "Gate fixture";
            user.publicAccount = publicAccount;
            em.persist(user);
        });
        return id;
    }
}
