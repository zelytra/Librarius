package zelytra.librarius.catalog.provider;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import zelytra.librarius.catalog.CatalogQuery;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the Solr query the provider builds, without going out to Open Library. */
class OpenLibraryProviderTest {

    /** Captures the query the provider sends, and answers nothing. */
    private static final class CapturingClient implements OpenLibraryClient {

        private String q;

        @Override
        public Uni<SearchResponse> search(String q, int limit, String fields) {
            this.q = q;
            return Uni.createFrom().item(new SearchResponse(List.of()));
        }
    }

    private final CapturingClient client = new CapturingClient();

    private OpenLibraryProvider provider(OpenLibraryClient client, Duration timeout) {
        OpenLibraryProvider provider = new OpenLibraryProvider();
        provider.client = client;
        provider.callTimeout = timeout;
        return provider;
    }

    private String queryFor(CatalogQuery criteria) {
        provider(client, Duration.ofSeconds(5)).search(criteria, 20);
        return client.q;
    }

    @Test
    void sendsTheFreeTextAsIs() {
        assertEquals("fourth wing", queryFor(CatalogQuery.of("fourth wing")));
    }

    @Test
    void turnsEveryAdvancedCriterionIntoAFieldedTerm() {
        String q = queryFor(new CatalogQuery("dune", "Frank Herbert", 1965, null, "Pocket", null));

        assertTrue(q.contains("author:\"Frank Herbert\""), q);
        assertTrue(q.contains("publisher:\"Pocket\""), q);
        assertTrue(q.contains("first_publish_year:\"1965\""), q);
        assertTrue(q.startsWith("dune"), q);
    }

    @Test
    void searchesTheIsbnOnItsOwnField() {
        assertEquals("isbn:\"9780441013593\"",
                queryFor(new CatalogQuery(null, null, null, null, null, "9780441013593")));
    }

    @Test
    void mapsTheLanguageToItsMarcCode() {
        // Open Library indexes languages as MARC codes; the client speaks ISO 639-1.
        assertEquals("language:\"fre\"",
                queryFor(new CatalogQuery(null, null, null, "fr", null, null)));
        assertEquals("language:\"jpn\"",
                queryFor(new CatalogQuery(null, null, null, "ja", null, null)));
        // Already a MARC code: passed through rather than dropped.
        assertEquals("language:\"ita\"",
                queryFor(new CatalogQuery(null, null, null, "ita", null, null)));
    }

    @Test
    void dropsTheQuotesTypedByTheUser() {
        // An unbalanced quote would break the Solr query and answer nothing at all.
        String q = queryFor(new CatalogQuery(null, "Herbert\" OR", null, null, null, null));

        assertEquals("author:\"Herbert OR\"", q);
        assertFalse(q.contains("\"\""), q);
    }

    @Test
    void givesUpOnAProviderThatNeverAnswers() {
        // `read-timeout` bounds silence, not slowness — Vert.x restarts that timer on every
        // chunk. The deadline below is the only thing that stops a cold fetch from holding a
        // database connection for as long as Open Library feels like taking.
        OpenLibraryProvider provider = provider(
                (q, limit, fields) -> Uni.createFrom().nothing(), Duration.ofMillis(200));

        long start = System.nanoTime();
        List<?> results = provider.search(CatalogQuery.of("never answers"), 20);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(results.isEmpty(), "a timed-out call degrades into an empty result");
        assertTrue(elapsedMs < 5_000, "the call must give up on its own, took " + elapsedMs + "ms");
    }
}
