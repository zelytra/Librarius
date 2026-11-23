package zelytra.librarius.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * Delivers push notifications through Firebase Cloud Messaging, HTTP v1 (#69) — which also
 * reaches iOS through APNs once the Firebase project holds the APNs key.
 *
 * <p>Plain JDK: a service-account JWT signed with {@code SHA256withRSA}, exchanged for an OAuth
 * access token kept until shortly before it expires, then one POST per device. That is the whole
 * of what the Firebase Admin SDK would do here, without its dependency tree.
 *
 * <p>With no service account configured ({@code librarius.push.fcm.service-account}, the path of
 * the JSON key Firebase hands out), {@link #enabled()} is false and nothing is ever sent: a
 * development stack or a cluster without the secret runs exactly as before.
 */
@ApplicationScoped
public class FcmSender {

    /** What became of one message. */
    public enum Outcome {
        DELIVERED,
        /** FCM no longer knows the device (app uninstalled, token rotated): forget it. */
        UNREGISTERED,
        FAILED
    }

    static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    /** An access token is renewed this long before Google says it expires. */
    private static final Duration TOKEN_MARGIN = Duration.ofMinutes(5);

    @ConfigProperty(name = "librarius.push.fcm.service-account")
    Optional<String> serviceAccountPath;

    @Inject
    ObjectMapper json;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /** The fields of the key file this needs; {@code null} until read, absent when there is none. */
    record ServiceAccount(String projectId, String clientEmail, PrivateKey privateKey, String tokenUri) {
    }

    private Optional<ServiceAccount> account;
    private String accessToken;
    private Instant accessTokenExpiry = Instant.EPOCH;

    /** Whether a service account is configured, and could be read. */
    public boolean enabled() {
        return account().isPresent();
    }

    /** Sends one message to one device. Never throws: a failure is logged and reported. */
    public Outcome send(String deviceToken, PushMessage message) {
        Optional<ServiceAccount> configured = account();
        if (configured.isEmpty()) {
            return Outcome.FAILED;
        }
        ServiceAccount sa = configured.get();
        try {
            String body = json.writeValueAsString(Map.of("message", Map.of(
                    "token", deviceToken,
                    "notification", Map.of("title", message.title(), "body", message.body()),
                    "data", Map.of("path", message.path()))));
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            "https://fcm.googleapis.com/v1/projects/" + sa.projectId() + "/messages:send"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + accessToken(sa))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return Outcome.DELIVERED;
            }
            if (response.statusCode() == 404 || response.body().contains("UNREGISTERED")) {
                return Outcome.UNREGISTERED;
            }
            Log.warnf("FCM refused a message: HTTP %d %s", response.statusCode(), response.body());
            return Outcome.FAILED;
        } catch (IOException | GeneralSecurityException e) {
            Log.warnf(e, "FCM could not be reached");
            return Outcome.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.FAILED;
        }
    }

    private synchronized String accessToken(ServiceAccount sa)
            throws IOException, InterruptedException, GeneralSecurityException {
        if (accessToken != null && Instant.now().isBefore(accessTokenExpiry.minus(TOKEN_MARGIN))) {
            return accessToken;
        }
        String form = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer",
                StandardCharsets.UTF_8) + "&assertion=" + assertion(sa, Instant.now());
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(sa.tokenUri()))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("token endpoint answered HTTP " + response.statusCode());
        }
        JsonNode answer = json.readTree(response.body());
        accessToken = answer.path("access_token").asText();
        accessTokenExpiry = Instant.now().plusSeconds(answer.path("expires_in").asLong(3600));
        return accessToken;
    }

    /** The signed JWT a service account trades for an access token. Package-private for its test. */
    String assertion(ServiceAccount sa, Instant now) throws GeneralSecurityException, IOException {
        Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();
        String header = url.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String claims = url.encodeToString(json.writeValueAsBytes(Map.of(
                "iss", sa.clientEmail(),
                "scope", SCOPE,
                "aud", sa.tokenUri(),
                "iat", now.getEpochSecond(),
                "exp", now.plusSeconds(3600).getEpochSecond())));
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(sa.privateKey());
        signer.update((header + "." + claims).getBytes(StandardCharsets.UTF_8));
        return header + "." + claims + "." + url.encodeToString(signer.sign());
    }

    /** Reads the key file once; a file that cannot be read disables the sender, loudly. */
    synchronized Optional<ServiceAccount> account() {
        if (account == null) {
            account = serviceAccountPath.filter(path -> !path.isBlank()).flatMap(this::read);
        }
        return account;
    }

    private Optional<ServiceAccount> read(String path) {
        if (!Files.exists(Path.of(path))) {
            // The chart always names the file; its Secret is optional (#69).
            Log.infof("Push disabled: no FCM service account at %s", path);
            return Optional.empty();
        }
        try {
            JsonNode key = json.readTree(Files.readString(Path.of(path)));
            String pem = key.path("private_key").asText()
                    .replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            PrivateKey privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
            String tokenUri = key.path("token_uri").asText("https://oauth2.googleapis.com/token");
            return Optional.of(new ServiceAccount(key.path("project_id").asText(),
                    key.path("client_email").asText(), privateKey, tokenUri));
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            Log.errorf(e, "Push disabled: the FCM service account at %s cannot be read", path);
            return Optional.empty();
        }
    }
}
