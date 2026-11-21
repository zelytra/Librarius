package zelytra.librarius.legal;

/**
 * The terms of service and privacy policy in force (#74). The documents themselves are pages of
 * the web app; the API only knows their version, so it can tell a member they have a version to
 * accept and refuse an acceptance of one the app does not show.
 *
 * <p>Raise {@link #CURRENT_VERSION} together with {@code LEGAL_VERSION} in the web app whenever
 * either document changes in substance: every member is then asked to accept again.
 */
public final class LegalTerms {

    public static final int CURRENT_VERSION = 1;

    private LegalTerms() {
    }
}
