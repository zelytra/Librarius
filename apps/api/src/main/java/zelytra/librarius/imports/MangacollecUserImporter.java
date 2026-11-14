package zelytra.librarius.imports;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Imports a Mangacollec collection from its member's pseudo alone (#456): the collection a
 * public profile shows, read from the same API the site loads it from, then parsed by
 * {@link MangacollecImporter}. A private profile, or the site refusing our server, is told
 * apart, and the reader is pointed at the collector bookmarklet, which reads the collection
 * with their own session and always works.
 */
@ApplicationScoped
public class MangacollecUserImporter implements LibraryImporter {

    static final String API = "https://api.mangacollec.com/v2/user/";

    /** What a Mangacollec pseudo can be: nothing that could leave the path segment. */
    private static final Pattern HANDLE = Pattern.compile("[\\w.-]{1,40}");

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .proxy(ProxySelector.getDefault())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public String source() {
        return MangacollecImporter.SOURCE;
    }

    @Override
    public List<ImportedBook> fetch(String handle) {
        String pseudo = handle == null ? "" : handle.trim().replaceFirst("^@", "");
        if (!HANDLE.matcher(pseudo).matches()) {
            throw new ImportException("Pseudo Mangacollec invalide : c'est celui de l'adresse de ton profil, "
                    + "mangacollec.com/user/ton-pseudo.");
        }
        HttpResponse<String> response;
        try {
            response = http.send(HttpRequest.newBuilder(URI.create(API
                                    + URLEncoder.encode(pseudo, StandardCharsets.UTF_8) + "/collection"))
                            .timeout(TIMEOUT)
                            .header("Accept", "application/json")
                            .header("User-Agent", "Librarius/1.0 (+https://github.com/zelytra/Librarius)")
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImportException(failureMessage(pseudo, 0));
        } catch (Exception e) {
            Log.warnf("Mangacollec import failed for %s: %s", pseudo, e.getMessage());
            throw new ImportException(failureMessage(pseudo, 0));
        }
        if (response.statusCode() / 100 != 2) {
            throw new ImportException(failureMessage(pseudo, response.statusCode()));
        }
        return MangacollecImporter.parse(response.body());
    }

    /** What the reader is told when the collection cannot be read by pseudo. */
    static String failureMessage(String pseudo, int status) {
        String collector = " Utilise le favori « Librarius : collecter » sur mangacollec.com, connecté à ton "
                + "compte : il lit ta collection avec ta propre session et fonctionne toujours.";
        if (status == 404) {
            return "Aucun profil Mangacollec « " + pseudo + " »." + collector;
        }
        if (status == 401 || status == 403) {
            return "Le profil Mangacollec « " + pseudo + " » n'est pas public, ou Mangacollec refuse notre "
                    + "serveur (HTTP " + status + ")." + collector;
        }
        return "Impossible de lire la collection Mangacollec de « " + pseudo + " »"
                + (status > 0 ? " (HTTP " + status + ")" : "") + "." + collector;
    }
}
