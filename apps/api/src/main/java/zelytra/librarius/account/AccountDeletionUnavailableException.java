package zelytra.librarius.account;

/**
 * The account could not be deleted, and <b>nothing was erased</b>.
 *
 * <p>Thrown when the identity provider refuses, is unreachable, or was never configured.
 * The message reaches the user, so it is in French like the import ones.
 */
public class AccountDeletionUnavailableException extends RuntimeException {

    public AccountDeletionUnavailableException(String message) {
        super(message);
    }
}
