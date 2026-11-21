package zelytra.librarius.legal;

/**
 * The terms of service and privacy policy in force (#74). The documents themselves are pages of
 * the web app; the API only knows their version, so it can tell a member they have a version to
 * accept and refuse an acceptance of one the app does not show.
 *
 * <p>Raise {@link #CURRENT_VERSION} whenever either document changes in substance, in the same
 * change as the text ({@code apps/web/src/features/legal/content.*.ts}): {@code /api/me} carries
 * it, and the app asks every member whose accepted version is lower to accept again.
 */
public final class LegalTerms {

    public static final int CURRENT_VERSION = 1;

    private LegalTerms() {
    }
}
