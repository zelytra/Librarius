package zelytra.librarius.opinion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * The few JSON calls the external opinions make (#453) — Open Library, AniList, Wikipedia —
 * each bounded to a few seconds and never failing the caller: a source that does not answer
 * simply contributes nothing. A bean of its own so the tests can stand in for the network.
 */
@ApplicationScoped
public class ExternalJson {

    static final String USER_AGENT = "Librarius/1.0 (+https://github.com/zelytra/Librarius)";

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final ObjectMapper json = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .proxy(ProxySelector.getDefault())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Optional<JsonNode> get(String url) {
        return send(HttpRequest.newBuilder(URI.create(url)).GET());
    }

    public Optional<JsonNode> post(String url, String body) {
        return send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    private Optional<JsonNode> send(HttpRequest.Builder request) {
        try {
            HttpResponse<String> response = http.send(request.timeout(TIMEOUT)
                            .header("User-Agent", USER_AGENT)
                            .header("Accept", "application/json")
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return Optional.empty();
            }
            return Optional.ofNullable(json.readTree(response.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            Log.debugf("External opinion call failed: %s", e.getMessage());
            return Optional.empty();
        }
    }
}
