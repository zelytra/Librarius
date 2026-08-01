package zelytra.librarius.trust;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.domain.Work;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scheduled promotion, end to end against the database (#180).
 *
 * <p>The test profile switches the timer off, so the job is driven by calling
 * {@link TrustEvaluator#evaluateAll()} directly, and lowers the thresholds
 * ({@code min-account-months=1}, {@code min-read-titles=2}) so a fixture need not seed months
 * of tenure and a whole shelf of reads. What is worth locking down here rather than in
 * {@link TrustEvaluatorTest} is everything the criteria alone cannot show: that a qualifying
 * account is stamped {@code trusted}/{@code trusted_at} in its own row, that this never touches
 * a neighbour's row, and that a second run neither re-stamps an already-trusted account nor
 * promotes one that still falls short.
 */
@QuarkusTest
class TrustEvaluationTest {

    @Inject
    TrustEvaluator evaluator;

    @Inject
    EntityManager em;

    /**
     * A qualifying account is promoted, and the promotion lands on its own row and nobody
     * else's — the per-user isolation the standing rule demands of every user-scoped read.
     */
    @Test
    void aQualifyingAccountIsPromotedWithoutTouchingAnother() {
        // Old enough and two titles finished: over both floors.
        String alice = seedUser(2, 2);
        // Old enough but only one finished title: short on volume.
        String tooFewReads = seedUser(1, 2);
        // Two finished titles but provisioned today: short on tenure.
        String tooYoung = seedUser(2, 0);
        // Brand new and empty: short on both.
        String bob = seedUser(0, 0);

        int promoted = evaluator.evaluateAll();

        assertTrue(promoted >= 1, "at least the qualifying account was promoted");
        assertTrue(trusted(alice), "the account clearing every floor is trusted");
        assertNotNull(trustedAt(alice), "and carries the day it earned the flag");

        assertFalse(trusted(tooFewReads), "too few finished titles is not trusted");
        assertNull(trustedAt(tooFewReads));
        assertFalse(trusted(tooYoung), "too little tenure is not trusted");
        assertNull(trustedAt(tooYoung));
        assertFalse(trusted(bob), "a brand-new empty account is not trusted");
        assertNull(trustedAt(bob));
    }

    /**
     * The evaluation only ever grants: a second run leaves an already-trusted account exactly
     * as it was — same flag, same timestamp — and promotes nobody who still falls short.
     */
    @Test
    void aSecondRunNeitherReStampsNorPromotesTheUnfit() {
        String alice = seedUser(3, 2);
        String bob = seedUser(0, 0);

        evaluator.evaluateAll();
        assertTrue(trusted(alice));
        OffsetDateTime earnedAt = trustedAt(alice);
        assertNotNull(earnedAt);

        int promotedSecondTime = evaluator.evaluateAll();

        assertEquals(0, promotedSecondTime, "nobody new the second time round");
        assertTrue(trusted(alice), "still trusted");
        assertEquals(earnedAt, trustedAt(alice), "the earned-at day is not moved by a re-run");
        assertFalse(trusted(bob), "and the unfit account is still not trusted");
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    /**
     * A fresh account with {@code readCount} titles marked {@code READ}, optionally backdated
     * so its tenure clears the test threshold.
     *
     * @param ageMonths months to backdate {@code created_at} by; 0 leaves it at now
     */
    private String seedUser(int readCount, int ageMonths) {
        String userId = "trust-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            AppUser user = new AppUser();
            user.id = userId;
            user.displayName = "Trust fixture";
            em.persist(user);

            for (int i = 0; i < readCount; i++) {
                Work work = new Work();
                work.kind = Kind.BOOK;
                work.title = "Trust " + UUID.randomUUID();
                em.persist(work);

                Edition edition = new Edition();
                edition.work = work;
                em.persist(edition);

                LibraryItem item = new LibraryItem();
                item.userId = userId;
                item.edition = edition;
                item.status = LibraryStatus.READ;
                em.persist(item);
            }
        });

        if (ageMonths > 0) {
            // created_at carries a database default and is not insertable through JPA, so the
            // only way to give a fixture a past is a native update after it is provisioned.
            OffsetDateTime backdated = OffsetDateTime.now().minusMonths(ageMonths).minusDays(1);
            QuarkusTransaction.requiringNew().run(() ->
                    em.createNativeQuery("update app_user set created_at = ?1 where id = ?2")
                            .setParameter(1, backdated)
                            .setParameter(2, userId)
                            .executeUpdate());
        }
        return userId;
    }

    private boolean trusted(String userId) {
        return QuarkusTransaction.requiringNew().call(() -> em.find(AppUser.class, userId).trusted);
    }

    private OffsetDateTime trustedAt(String userId) {
        return QuarkusTransaction.requiringNew()
                .call(() -> em.find(AppUser.class, userId).trustedAt);
    }
}
