package zelytra.librarius.feedback;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The key that opens the reports to agents (#518): closed until one is configured. */
class AgentKeyTest {

    @Test
    void noKeyConfiguredOpensNothing() {
        AgentKey key = new AgentKey(Optional.empty());
        assertFalse(key.matches(null));
        assertFalse(key.matches(""));
        assertFalse(key.matches("anything"));
    }

    @Test
    void aBlankKeyConfiguredOpensNothing() {
        AgentKey key = new AgentKey(Optional.of("   "));
        assertFalse(key.matches("   "));
        assertFalse(key.matches(""));
    }

    @Test
    void onlyTheConfiguredKeyMatches() {
        AgentKey key = new AgentKey(Optional.of("s3cret-key"));
        assertTrue(key.matches("s3cret-key"));
        assertFalse(key.matches("s3cret-ke"));
        assertFalse(key.matches("s3cret-key "));
        assertFalse(key.matches(null));
    }
}
