package zelytra.librarius.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The display name a new account starts with (#538). Other members see it — on the feed, the
 * followers list, a review — so it must never be the e-mail address. An account created through
 * Google or Apple carries no name (only the e-mail scope is asked for) and Keycloak sets its
 * username to the e-mail: it starts with the neutral default, and the reader picks their own name
 * in Settings.
 */
class CurrentUserDisplayNameTest {

    @Test
    void takesTheNameTheTokenCarries() {
        assertEquals("Alice Lectrice", CurrentUser.initialDisplayName("Alice Lectrice", "alice"));
    }

    @Test
    void fallsBackToTheUsernameWhenThereIsNoName() {
        assertEquals("alice", CurrentUser.initialDisplayName(null, "alice"));
        assertEquals("alice", CurrentUser.initialDisplayName("  ", "alice"));
    }

    @Test
    void anAccountFromGoogleOrAppleDoesNotShowItsEmailAddress() {
        assertEquals("Lecteur", CurrentUser.initialDisplayName(null, "reader@gmail.com"));
    }

    @Test
    void anAppleRelayAddressIsNotShownEither() {
        assertEquals("Lecteur",
                CurrentUser.initialDisplayName(null, "x7k2@privaterelay.appleid.com"));
    }

    @Test
    void aNameThatIsAnEmailAddressIsSkippedToo() {
        assertEquals("alice", CurrentUser.initialDisplayName("alice@biblio.app", "alice"));
    }
}
