package zelytra.librarius.account;

import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stands in for Keycloak across the whole test suite.
 *
 * <p>A CDI alternative rather than a Mockito mock: {@code @InjectMock} needs Byte Buddy to
 * attach an agent to the running JVM, which does not work inside the container the
 * verification command uses — see CONVENTIONS § "A machine without a JDK or Docker". This is
 * plain CDI, so it works everywhere.
 *
 * <p>Nothing here talks to the Dev Services Keycloak: deleting the realm user would break
 * every later test that signs in as them, and what the tests need to pin down is what the
 * <em>application</em> does with each answer, not that an HTTP DELETE reaches Keycloak.
 *
 * <p>The state is reached through methods and never through fields. This bean is normal
 * scoped, so what a test injects is a client proxy: it forwards calls, but a field written
 * on it lands on the proxy and the real instance never sees it — which is exactly the shape
 * of a test that passes while asserting nothing.
 */
@Mock
@ApplicationScoped
public class RecordingKeycloakAccountDeleter implements KeycloakAccountDeleter {

    private volatile Outcome nextOutcome = Outcome.DELETED;

    private final List<String> deleted = new CopyOnWriteArrayList<>();

    /** What the following calls answer, until {@link #reset()}. */
    public void willAnswer(Outcome outcome) {
        nextOutcome = outcome;
    }

    /** Subjects this was asked to delete, in order. */
    public List<String> deletedSubjects() {
        return List.copyOf(deleted);
    }

    /** Back to the nominal answer, and no history. */
    public void reset() {
        nextOutcome = Outcome.DELETED;
        deleted.clear();
    }

    @Override
    public Outcome delete(String subject) {
        Outcome outcome = nextOutcome;
        if (outcome == Outcome.DELETED || outcome == Outcome.ALREADY_ABSENT) {
            deleted.add(subject);
        }
        return outcome;
    }
}
