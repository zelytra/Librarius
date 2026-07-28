package zelytra.librarius.catalog.provider;

import org.junit.jupiter.api.Test;
import zelytra.librarius.catalog.CatalogQuery;

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
        public SearchResponse search(String q, int limit, String fields) {
            this.q = q;
            return new SearchResponse(List.of());
        }
    }

    private final CapturingClient client = new CapturingClient();

    private String queryFor(CatalogQuery criteria) {
        OpenLibraryProvider provider = new OpenLibraryProvider();
        provider.client = client;
        provider.search(criteria, 20);
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
}
