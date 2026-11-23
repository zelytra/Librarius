package zelytra.librarius.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** #69: the sender's two jobs that need no network — reading the key, signing the assertion. */
class FcmSenderTest {

    private final ObjectMapper json = new ObjectMapper();

    private FcmSender sender(Optional<String> path) {
        FcmSender sender = new FcmSender();
        sender.json = json;
        sender.serviceAccountPath = path;
        return sender;
    }

    @Test
    void withNoServiceAccountNothingIsEverSent() {
        assertFalse(sender(Optional.empty()).enabled());
        assertFalse(sender(Optional.of(" ")).enabled());
        assertEquals(FcmSender.Outcome.FAILED, sender(Optional.empty()).send("t", new PushMessage("a", "b", "/")));
    }

    @Test
    void anUnreadableKeyDisablesTheSender(@TempDir Path dir) throws Exception {
        Path key = dir.resolve("broken.json");
        Files.writeString(key, "{ \"private_key\": \"not a key\" }");
        assertFalse(sender(Optional.of(key.toString())).enabled());
    }

    @Test
    void signsAnAssertionGoogleCanVerify(@TempDir Path dir) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        Path key = dir.resolve("service-account.json");
        Files.writeString(key, json.writeValueAsString(Map.of(
                "project_id", "librarius-test", "client_email", "push@librarius-test.iam.gserviceaccount.com",
                "private_key", pem, "token_uri", "https://oauth2.googleapis.com/token")));
        FcmSender sender = sender(Optional.of(key.toString()));
        assertTrue(sender.enabled());

        Instant now = Instant.parse("2031-01-01T09:00:00Z");
        String[] parts = sender.assertion(sender.account().orElseThrow(), now).split("\\.");

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(pair.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(Base64.getUrlDecoder().decode(parts[2])));

        JsonNode claims = json.readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertEquals("push@librarius-test.iam.gserviceaccount.com", claims.path("iss").asText());
        assertEquals(FcmSender.SCOPE, claims.path("scope").asText());
        assertEquals(now.getEpochSecond() + 3600, claims.path("exp").asLong());
    }
}
