package zelytra.librarius.domain.repository;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.domain.Author;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-user half of the author tables.
 *
 * <p>A follow is private data on a shared catalog row, so this exercises both halves of what
 * that means: it is idempotent — a {@code PUT} repeated by a client that lost the response
 * must not fail on the primary key — and it is invisible to everyone else. The isolation case
 * is mandatory on every user-scoped table since
 * <a href="https://github.com/zelytra/Librarius/issues/39">#39</a>; the endpoints that will
 * expose these methods are #196's, and they inherit whatever is asserted here.
 */
@QuarkusTest
class AuthorFollowTest {

    @Inject
    AuthorFollowRepository follows;

    @Inject
    EntityManager em;

    @Test
    void followingIsIdempotent() {
        String alice = seedUser();
        UUID author = seedAuthor();

        QuarkusTransaction.requiringNew().run(() -> {
            follows.follow(alice, author);
            follows.follow(alice, author);
        });

        assertTrue(follows.isFollowing(alice, author));
        assertEquals(Set.of(author), follows.followedAuthorIds(alice));
    }

    @Test
    void unfollowingIsIdempotentAndLeavesTheAuthorAlone() {
        String alice = seedUser();
        UUID author = seedAuthor();

        QuarkusTransaction.requiringNew().run(() -> follows.follow(alice, author));
        QuarkusTransaction.requiringNew().run(() -> {
            follows.unfollow(alice, author);
            follows.unfollow(alice, author);
        });

        assertFalse(follows.isFollowing(alice, author));
        assertEquals(Set.of(), follows.followedAuthorIds(alice));
        // The catalog row is shared: dropping a follow may never drop the author with it.
        assertNotNull(em.find(Author.class, author), "the author survives the unfollow");
    }

    /**
     * The standing rule: one account never reads another's. Both follow authors of their own
     * and one in common, so a leak would show up as an extra identifier rather than as an
     * empty answer that any bug would also produce.
     */
    @Test
    void oneUsersFollowsAreInvisibleToAnother() {
        String alice = seedUser();
        String bob = seedUser();
        UUID hers = seedAuthor();
        UUID his = seedAuthor();
        UUID shared = seedAuthor();

        QuarkusTransaction.requiringNew().run(() -> {
            follows.follow(alice, hers);
            follows.follow(alice, shared);
            follows.follow(bob, his);
            follows.follow(bob, shared);
        });

        assertEquals(Set.of(hers, shared), follows.followedAuthorIds(alice));
        assertEquals(Set.of(his, shared), follows.followedAuthorIds(bob));
        assertFalse(follows.isFollowing(alice, his), "alice does not follow bob's author");
        assertFalse(follows.isFollowing(bob, hers), "bob does not follow alice's author");

        // And unfollowing the shared author only ever ends one of the two follows.
        QuarkusTransaction.requiringNew().run(() -> follows.unfollow(alice, shared));

        assertEquals(Set.of(hers), follows.followedAuthorIds(alice));
        assertEquals(Set.of(his, shared), follows.followedAuthorIds(bob));
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    /** author_follow.user_id is a foreign key onto app_user. */
    private String seedUser() {
        String userId = "follow-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            AppUser user = new AppUser();
            user.id = userId;
            user.displayName = "Follow fixture";
            em.persist(user);
        });
        return userId;
    }

    private UUID seedAuthor() {
        Author author = new Author();
        author.name = "Follow fixture " + UUID.randomUUID();
        author.nameKey = "follow-fixture-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> em.persist(author));
        return author.id;
    }
}
