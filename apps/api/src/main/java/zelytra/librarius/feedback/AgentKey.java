package zelytra.librarius.feedback;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * The key an agent presents to read and triage the reports outside Keycloak (#518), set by
 * {@code LIBRARIUS_AGENT_API_KEY}. With none configured, or a blank one, nothing matches: the
 * endpoint stays closed rather than open to anyone.
 */
@ApplicationScoped
public class AgentKey {

    private final byte[] expected;

    public AgentKey(@ConfigProperty(name = "librarius.agent.api-key") Optional<String> configured) {
        this.expected = configured.map(String::strip).filter(key -> !key.isEmpty())
                .map(key -> key.getBytes(StandardCharsets.UTF_8)).orElse(null);
    }

    /** Whether {@code presented} is the configured key, compared in constant time. */
    public boolean matches(String presented) {
        return expected != null && presented != null
                && MessageDigest.isEqual(expected, presented.getBytes(StandardCharsets.UTF_8));
    }
}
