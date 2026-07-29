package zelytra.librarius.catalog.provider;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import zelytra.librarius.catalog.CatalogQuery;
import zelytra.librarius.catalog.CatalogResult;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the CQL the provider builds and the records it reads back, without calling the BnF. */
class BnfProviderTest {

    private static final String DUNE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <srw:searchRetrieveResponse xmlns:srw="http://www.loc.gov/zing/srw/">
              <srw:numberOfRecords>2</srw:numberOfRecords>
              <srw:records>
                <srw:record>
                  <srw:recordSchema>dublincore</srw:recordSchema>
                  <srw:recordData>
                    <dc xmlns="http://purl.org/dc/elements/1.1/">
                      <title>Dune</title>
                      <creator>Herbert, Frank (1920-1986). Auteur du texte</creator>
                      <publisher>Pocket</publisher>
                      <date>2001</date>
                      <language>fre</language>
                      <identifier>http://catalogue.bnf.fr/ark:/12148/cb37652488r</identifier>
                      <identifier>978-2-266-11624-2 (br.) : 8,90 EUR</identifier>
                    </dc>
                  </srw:recordData>
                </srw:record>
                <srw:record>
                  <srw:recordData>
                    <dc xmlns="http://purl.org/dc/elements/1.1/">
                      <title>Le messie de Dune</title>
                      <creator>Herbert, Frank (1920-1986). Auteur du texte</creator>
                      <date>cop. 1975</date>
                    </dc>
                  </srw:recordData>
                </srw:record>
              </srw:records>
            </srw:searchRetrieveResponse>
            """;

    /** Captures the request the provider sends, and answers the canned envelope. */
    private static final class CapturingClient implements BnfClient {

        private String query;

        private int maximumRecords;

        private final String answer;

        private CapturingClient(String answer) {
            this.answer = answer;
        }

        @Override
        public Uni<String> search(String version, String operation, String query,
                String recordSchema, int maximumRecords) {
            this.query = query;
            this.maximumRecords = maximumRecords;
            return Uni.createFrom().item(answer);
        }
    }

    private static BnfProvider provider(BnfClient client, Duration timeout) {
        BnfProvider provider = new BnfProvider();
        provider.client = client;
        provider.callTimeout = timeout;
        return provider;
    }

    private static String cqlFor(CatalogQuery criteria) {
        CapturingClient client = new CapturingClient(DUNE);
        provider(client, Duration.ofSeconds(5)).search(criteria, 20);
        return client.query;
    }

    private static List<CatalogResult> resultsOf(String xml, CatalogQuery criteria) {
        return provider(new CapturingClient(xml), Duration.ofSeconds(5)).search(criteria, 20);
    }

    @Test
    void sendsTheFreeTextOnTheAnywhereIndex() {
        assertEquals("bib.anywhere all \"fourth wing\"", cqlFor(CatalogQuery.of("fourth wing")));
    }

    @Test
    void joinsEveryAdvancedCriterionWithAnd() {
        String cql = cqlFor(new CatalogQuery("dune", "Frank Herbert", null, null, "Pocket", null));

        assertTrue(cql.contains("bib.anywhere all \"dune\""), cql);
        assertTrue(cql.contains("bib.author all \"Frank Herbert\""), cql);
        assertTrue(cql.contains("bib.publisher all \"Pocket\""), cql);
        assertEquals(2, cql.split(" and ").length - 1, cql);
    }

    @Test
    void searchesTheIsbnOnItsOwnIndex() {
        assertEquals("bib.isbn all \"9782266116242\"",
                cqlFor(new CatalogQuery(null, null, null, null, null, "9782266116242")));
    }

    @Test
    void mapsTheLanguageToItsMarcCode() {
        // The BnF indexes languages as MARC codes; the client speaks ISO 639-1.
        assertEquals("bib.language all \"fre\"",
                cqlFor(new CatalogQuery(null, null, null, "fr", null, null)));
        // Already a MARC code: passed through rather than dropped.
        assertEquals("bib.language all \"jpn\"",
                cqlFor(new CatalogQuery(null, null, null, "jpn", null, null)));
    }

    @Test
    void dropsTheQuotesTypedByTheUser() {
        // An unbalanced quote would make the BnF answer a diagnostic instead of records.
        String cql = cqlFor(new CatalogQuery(null, "Herbert\" or", null, null, null, null));

        assertEquals("bib.author all \"Herbert or\"", cql);
        assertFalse(cql.contains("\"\""), cql);
    }

    @Test
    void doesNotCallTheBnfForAYearOnItsOwn() {
        // There is nothing to search for: every book printed that year is not an answer.
        CapturingClient client = new CapturingClient(DUNE);

        assertTrue(provider(client, Duration.ofSeconds(5))
                .search(new CatalogQuery(null, null, 1965, null, null, null), 20).isEmpty());
        assertNull(client.query, "no criterion the BnF can index, so no call");
    }

    @Test
    void readsTheDublinCoreRecord() {
        CatalogResult dune = resultsOf(DUNE, CatalogQuery.of("dune")).get(0);

        assertEquals("BOOK", dune.kind());
        assertEquals("Dune", dune.title());
        // The authority heading is put back in reading order, so that the same novel found
        // through Open Library ("Frank Herbert") merges with this one instead of being listed
        // twice.
        assertEquals("Frank Herbert", dune.authors());
        assertEquals("Pocket", dune.publisher());
        assertEquals(2001, dune.year());
        assertEquals("fre", dune.language());
        assertEquals("bnf", dune.provider());
    }

    @Test
    void picksTheIsbnOutOfTheIdentifierSentence() {
        // "978-2-266-11624-2 (br.) : 8,90 EUR" — the price must not become part of the number.
        assertEquals("9782266116242", resultsOf(DUNE, CatalogQuery.of("dune")).get(0).isbn13());
    }

    @Test
    void keepsTheArkAsTheProviderReference() {
        assertEquals("ark:/12148/cb37652488r",
                resultsOf(DUNE, CatalogQuery.of("dune")).get(0).providerRef());
    }

    @Test
    void readsTheYearOutOfAWordedDate() {
        // Dublin Core dates are not years: "cop. 1975", "DL 2001".
        assertEquals(1975, resultsOf(DUNE, CatalogQuery.of("dune")).get(1).year());
    }

    @Test
    void filtersTheYearOnTheRecordsThemselves() {
        List<CatalogResult> results =
                resultsOf(DUNE, new CatalogQuery("dune", null, 1975, null, null, null));

        assertEquals(1, results.size());
        assertEquals("Le messie de Dune", results.get(0).title());
    }

    @Test
    void asksForAFullPageWhenTheYearIsFilteredHere() {
        CapturingClient client = new CapturingClient(DUNE);
        BnfProvider provider = provider(client, Duration.ofSeconds(5));

        provider.search(CatalogQuery.of("dune"), 5);
        assertEquals(5, client.maximumRecords, "no local filter: ask for what is wanted");

        provider.search(new CatalogQuery("dune", null, 1975, null, null, null), 5);
        assertTrue(client.maximumRecords > 5, "a locally filtered year needs a page to filter");
    }

    @Test
    void ignoresARecordWithoutATitle() {
        String xml = """
                <srw:searchRetrieveResponse xmlns:srw="http://www.loc.gov/zing/srw/">
                  <srw:records>
                    <srw:record><srw:recordData>
                      <dc xmlns="http://purl.org/dc/elements/1.1/"><date>1965</date></dc>
                    </srw:recordData></srw:record>
                  </srw:records>
                </srw:searchRetrieveResponse>
                """;

        assertTrue(resultsOf(xml, CatalogQuery.of("dune")).isEmpty());
    }

    @Test
    void degradesToNoResultWhenTheAnswerIsNotTheExpectedXml() {
        // A gateway error page instead of an SRU envelope must not reach CatalogService as an
        // exception: the aggregate has to go on with the other book provider's results.
        assertTrue(resultsOf("<html><body>502 Bad Gateway</body></html>",
                CatalogQuery.of("dune")).isEmpty());
        assertTrue(resultsOf("not xml at all", CatalogQuery.of("dune")).isEmpty());
        assertTrue(resultsOf("", CatalogQuery.of("dune")).isEmpty());
    }

    @Test
    void degradesToNoResultWhenTheProviderIsUnreachable() {
        BnfProvider provider = provider(
                (version, operation, query, schema, max) ->
                        Uni.createFrom().failure(new IllegalStateException("connection refused")),
                Duration.ofSeconds(5));

        assertTrue(provider.search(CatalogQuery.of("dune"), 20).isEmpty());
    }

    @Test
    void givesUpOnAProviderThatNeverAnswers() {
        // `read-timeout` bounds silence, not slowness. The deadline below is the only thing
        // that stops a cold fetch from holding a database connection indefinitely.
        BnfProvider provider = provider(
                (version, operation, query, schema, max) -> Uni.createFrom().nothing(),
                Duration.ofMillis(200));

        long start = System.nanoTime();
        List<CatalogResult> results = provider.search(CatalogQuery.of("never answers"), 20);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(results.isEmpty(), "a timed-out call degrades into an empty result");
        assertTrue(elapsedMs < 5_000, "the call must give up on its own, took " + elapsedMs + "ms");
    }

    @Test
    void refusesADoctypeInTheAnswer() {
        // The answer comes from a third party: an entity declaration must not be expanded.
        String bomb = """
                <?xml version="1.0"?>
                <!DOCTYPE lolz [<!ENTITY lol "lol">]>
                <srw:searchRetrieveResponse xmlns:srw="http://www.loc.gov/zing/srw/">
                  <srw:records><srw:record><srw:recordData>
                    <dc xmlns="http://purl.org/dc/elements/1.1/"><title>&lol;</title></dc>
                  </srw:recordData></srw:record></srw:records>
                </srw:searchRetrieveResponse>
                """;

        assertTrue(resultsOf(bomb, CatalogQuery.of("dune")).isEmpty());
    }

    @Test
    void putsAnAuthorityHeadingBackInReadingOrder() {
        assertEquals("Frank Herbert",
                BnfProvider.normalizeCreator("Herbert, Frank (1920-1986). Auteur du texte"));
        // No life dates to cut at: the role is the trailing plain phrase.
        assertEquals("Rebecca Yarros",
                BnfProvider.normalizeCreator("Yarros, Rebecca. Auteur du texte"));
        // Initials end in a period too, and must not be mistaken for a role.
        assertEquals("J. R. R. Tolkien", BnfProvider.normalizeCreator("Tolkien, J. R. R."));
        // A single-element name has nothing to swap.
        assertEquals("Hergé", BnfProvider.normalizeCreator("Hergé (1907-1983)"));
    }
}
