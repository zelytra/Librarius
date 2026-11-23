package zelytra.librarius.contribution;

import jakarta.enterprise.context.ApplicationScoped;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.net.URI;

/**
 * Fetches a store page over https with a short timeout and a capped body. Redirects are
 * followed by hand, and only while they stay on a known store: a redirect is the classic way
 * around a host allowlist.
 */
@ApplicationScoped
public class JsoupStorePageFetcher implements StorePageFetcher {

    private static final int TIMEOUT_MS = 6_000;
    private static final int MAX_BODY_BYTES = 3 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 3;
    /** Stores answer a bare client with a bot wall more often than a browser-like one. */
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; Librarius/1.0; +https://github.com/zelytra/Librarius)";

    @Override
    public String fetch(String url) throws IOException {
        String current = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (SaleStore.of(current).isEmpty()) {
                throw new IOException("Not a known store: " + current);
            }
            Connection.Response response = Jsoup.connect(current)
                    .userAgent(USER_AGENT)
                    .header("Accept-Language", "fr-FR,fr;q=0.9")
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_BYTES)
                    .followRedirects(false)
                    .ignoreHttpErrors(true)
                    .execute();
            int status = response.statusCode();
            if (status >= 300 && status < 400 && response.hasHeader("Location")) {
                current = URI.create(current).resolve(response.header("Location")).toString();
                continue;
            }
            if (status != 200) {
                throw new IOException("Store answered " + status);
            }
            return response.body();
        }
        throw new IOException("Too many redirects");
    }
}
