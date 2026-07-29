package zelytra.librarius.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Deletes a Keycloak account through the admin REST API, on behalf of a service account.
 *
 * <p>Two calls and no extension: a client-credentials token from the realm's token endpoint,
 * then {@code DELETE /admin/realms/{realm}/users/{id}}. The Keycloak admin client extension
 * would do the same thing while dragging in a JAX-RS client stack and a build-time
 * configuration this deployment does not otherwise need.
 *
 * <p><b>It ships switched off.</b> No credential is invented here, and none is committed
 * anywhere: {@code librarius.keycloak.admin.enabled} defaults to {@code false}, and an
 * unconfigured instance reports {@link Outcome#NOT_CONFIGURED} — on which the caller refuses
 * to erase anything. What the maintainer has to create is written down in
 * {@code docs/DEPLOYMENT.md}.
 */
@ApplicationScoped
public class KeycloakAdminAccountDeleter implements KeycloakAccountDeleter {

    private static final Logger LOG = Logger.getLogger(KeycloakAdminAccountDeleter.class);

    /** Same bounds as the catalog clients: a slow provider degrades, it does not hang. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    /** What may be pasted into an admin URL as a user identifier. */
    private static final Pattern SAFE_SUBJECT = Pattern.compile("[A-Za-z0-9._:@-]{1,255}");

    @ConfigProperty(name = "librarius.keycloak.admin.enabled", defaultValue = "false")
    boolean enabled;

    /** Base URL of Keycloak, without the realm — e.g. {@code http://keycloak:8081/auth}. */
    @ConfigProperty(name = "librarius.keycloak.admin.server-url", defaultValue = "")
    String serverUrl;

    @ConfigProperty(name = "librarius.keycloak.admin.realm", defaultValue = "librarius")
    String realm;

    @ConfigProperty(name = "librarius.keycloak.admin.client-id", defaultValue = "")
    String clientId;

    @ConfigProperty(name = "librarius.keycloak.admin.client-secret", defaultValue = "")
    String clientSecret;

    @Inject
    ObjectMapper json;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    @Override
    public Outcome delete(String subject) {
        if (!configured()) {
            LOG.warn("Account deletion requested but no Keycloak service account is configured; "
                    + "refusing to erase application data while the login still exists.");
            return Outcome.NOT_CONFIGURED;
        }
        String token = accessToken();
        if (token == null) {
            return Outcome.FAILED;
        }
        return deleteUser(token, subject);
    }

    private boolean configured() {
        return enabled && !serverUrl.isBlank() && !clientId.isBlank() && !clientSecret.isBlank();
    }

    /** Client-credentials grant. The secret never leaves this method, logs included. */
    private String accessToken() {
        String body = "grant_type=client_credentials"
                + "&client_id=" + form(clientId)
                + "&client_secret=" + form(clientSecret);
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        base() + "/realms/" + form(realm) + "/protocol/openid-connect/token"))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                LOG.errorf("Keycloak refused the service account token (HTTP %d)",
                        response.statusCode());
                return null;
            }
            JsonNode node = json.readTree(response.body()).get("access_token");
            return node == null ? null : node.asText();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Interrupted while asking Keycloak for a service account token");
            return null;
        } catch (Exception e) {
            LOG.error("Could not obtain a Keycloak service account token", e);
            return null;
        }
    }

    private Outcome deleteUser(String token, String subject) {
        if (!SAFE_SUBJECT.matcher(subject).matches()) {
            // A Keycloak subject is a UUID. Anything else has no business being pasted into
            // an admin URL, whatever the token that carried it claims.
            LOG.error("Refusing to delete a Keycloak account whose subject is not a plain "
                    + "identifier");
            return Outcome.FAILED;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        base() + "/admin/realms/" + form(realm) + "/users/" + subject))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .DELETE()
                .build();
        try {
            HttpResponse<Void> response =
                    http.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status / 100 == 2) {
                return Outcome.DELETED;
            }
            if (status == 404) {
                // Nothing left to delete: a retry after a half-finished deletion must be
                // able to finish the job rather than stall on the identity that is gone.
                LOG.infof("Keycloak has no account %s left to delete", subject);
                return Outcome.ALREADY_ABSENT;
            }
            LOG.errorf("Keycloak refused to delete account %s (HTTP %d)", subject, status);
            return Outcome.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.errorf("Interrupted while deleting Keycloak account %s", subject);
            return Outcome.FAILED;
        } catch (Exception e) {
            LOG.errorf(e, "Could not reach Keycloak to delete account %s", subject);
            return Outcome.FAILED;
        }
    }

    private String base() {
        return serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
    }

    private static String form(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
