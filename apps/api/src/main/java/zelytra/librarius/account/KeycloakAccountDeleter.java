package zelytra.librarius.account;

/**
 * Deletes the identity behind an account, on the identity provider.
 *
 * <p>An interface because the two things that erase an account live in different systems and
 * fail independently: the rows are ours, the credentials are Keycloak's. Deleting the rows
 * while the account still exists would leave someone able to sign in to a library that is no
 * longer there — and, worse, would hand them a freshly provisioned empty one, which looks
 * exactly like data loss. So the identity goes first, and the caller only touches the
 * database once this has reported success.
 */
public interface KeycloakAccountDeleter {

    /** What happened on the identity provider. */
    enum Outcome {
        /** The account was deleted. */
        DELETED,
        /** There was nothing to delete — already gone, and the erasure may proceed. */
        ALREADY_ABSENT,
        /** No service account is configured: the deployment cannot delete accounts at all. */
        NOT_CONFIGURED,
        /** Keycloak was reachable or not, and did not delete the account. */
        FAILED
    }

    /**
     * @param subject the Keycloak {@code sub}, which is also the {@code app_user} identifier
     */
    Outcome delete(String subject);
}
