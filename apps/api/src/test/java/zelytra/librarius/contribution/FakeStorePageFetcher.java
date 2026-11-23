package zelytra.librarius.contribution;

import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stands in for the store websites across the whole test suite (#620): no test reaches the
 * network, and a page a test did not {@link #serve} fails to load, like a store that blocks bots.
 *
 * <p>A CDI alternative rather than a Mockito mock, for the reason given on
 * {@code RecordingKeycloakAccountDeleter}; the state is reached through methods only.
 */
@Mock
@ApplicationScoped
public class FakeStorePageFetcher implements StorePageFetcher {

    private final Map<String, String> pages = new ConcurrentHashMap<>();

    /** What {@code url} answers until {@link #reset()}. */
    public void serve(String url, String html) {
        pages.put(url, html);
    }

    public void reset() {
        pages.clear();
    }

    @Override
    public String fetch(String url) throws IOException {
        String html = pages.get(url);
        if (html == null) {
            throw new IOException("Not served in this test: " + url);
        }
        return html;
    }
}
