package zelytra.librarius.account;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import zelytra.librarius.account.AccountEraser.Erased;
import zelytra.librarius.account.KeycloakAccountDeleter.Outcome;

import java.time.Instant;

/**
 * Complete erasure of an account (GDPR art. 17).
 *
 * <p>Two systems have to forget the user, and the order is the whole point:
 *
 * <ol>
 *   <li><b>Keycloak first.</b> If it refuses, or is not configured, nothing is erased and the
 *       call fails. Erasing the library while the login survives would give the user a fresh
 *       empty account on their next sign-in — indistinguishable from having lost everything,
 *       and irreversible.</li>
 *   <li><b>The database second</b>, as a single delete the schema cascades from.</li>
 * </ol>
 *
 * <p>The window between the two is not zero: should the delete fail after Keycloak has
 * accepted, the rows are orphaned and the user can no longer ask again. That is logged at
 * error level with the identifier needed to finish the job by hand, and it is the safe side
 * of the trade — the other ordering loses data on a far more likely failure.
 */
@ApplicationScoped
public class AccountDeletionService {

    private static final Logger LOG = Logger.getLogger(AccountDeletionService.class);

    @Inject
    KeycloakAccountDeleter keycloak;

    @Inject
    AccountEraser eraser;

    /**
     * @return what was erased, or {@code null} when the caller had no application data —
     *         the identity is deleted either way
     * @throws AccountDeletionUnavailableException when the identity provider did not delete
     *                                             the account; nothing has been erased
     */
    public Erased delete(String userId) {
        Outcome outcome = keycloak.delete(userId);
        if (outcome == Outcome.NOT_CONFIGURED) {
            throw new AccountDeletionUnavailableException(
                    "La suppression de compte n'est pas disponible sur cette instance. "
                            + "Contacte l'administrateur : tes données n'ont pas été touchées.");
        }
        if (outcome == Outcome.FAILED) {
            throw new AccountDeletionUnavailableException(
                    "La suppression a échoué côté authentification. Rien n'a été supprimé, "
                            + "réessaie dans quelques minutes.");
        }

        Erased erased;
        try {
            erased = eraser.erase(userId);
        } catch (RuntimeException e) {
            LOG.errorf(e, "Keycloak account %s was deleted but its application data could not "
                    + "be erased; the rows must be removed by hand", userId);
            throw e;
        }

        // The deletion log: when, and for which technical identifier. No email, no display
        // name, no title — the point of the record is to prove the erasure happened, not to
        // keep a trace of who it was.
        LOG.infof("Account erased: subject=%s at=%s keycloak=%s items=%d wishes=%d goals=%d "
                        + "categories=%d follows=%d",
                userId, Instant.now(), outcome,
                erased == null ? 0 : erased.libraryItems(),
                erased == null ? 0 : erased.wishlistItems(),
                erased == null ? 0 : erased.goals(),
                erased == null ? 0 : erased.categories(),
                erased == null ? 0 : erased.seriesFollows());
        return erased;
    }
}
