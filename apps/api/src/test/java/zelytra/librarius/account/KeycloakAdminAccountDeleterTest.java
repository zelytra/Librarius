package zelytra.librarius.account;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import zelytra.librarius.account.KeycloakAccountDeleter.Outcome;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link KeycloakAdminAccountDeleter} itself, constructed by CDI.
 *
 * <p>Every other test reaches {@code KeycloakAccountDeleter} through the interface, which
 * {@link RecordingKeycloakAccountDeleter}'s {@code @Mock} intercepts — so this class is never
 * actually instantiated anywhere else in the suite, and its {@code @ConfigProperty} fields
 * are never actually resolved. That hid a real bug: {@code librarius.keycloak.admin.server-url}
 * and its two neighbours were plain {@code String} fields with {@code defaultValue = ""}, but
 * {@code application.properties} declares them unconditionally
 * ({@code ${KEYCLOAK_ADMIN_SERVER_URL:}}), so the key is always *present* — empty, on an
 * instance that leaves the variable unset, but present. SmallRye only falls back to a
 * {@code @ConfigProperty}'s own default when the key is absent, so an empty-but-present value
 * went to the String converter instead, which refused it and crashed the whole application at
 * startup. That reproduced on every instance that left account deletion off — e2e and every
 * qualification deployment — and nowhere in this suite, since nothing here forced this bean
 * into existence. Injecting the concrete class, rather than the interface, does.
 */
@QuarkusTest
class KeycloakAdminAccountDeleterTest {

    @Inject
    KeycloakAdminAccountDeleter deleter;

    /**
     * No {@code KEYCLOAK_ADMIN_*} variable is set for the test profile, which is exactly the
     * shape that used to crash the JVM before the fields became {@code Optional<String>}. If
     * this bean cannot even be constructed, the whole test class fails to instantiate rather
     * than this one assertion failing — which is still a clear enough signal.
     */
    @Test
    void reportsNotConfiguredRatherThanFailingToStart() {
        assertEquals(Outcome.NOT_CONFIGURED, deleter.delete("00000000-0000-0000-0000-000000000000"));
    }
}
