package zelytra.librarius.catalog.provider;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import zelytra.librarius.catalog.CatalogQuery;
import zelytra.librarius.catalog.CatalogResult;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the CQL the provider builds and the UNIMARC records it reads back, without calling
 * the BnF. The fixtures under {@code catalog/bnf} are real answers, recorded with
 * {@code bib.title adj "<q>"} ({@code .phrase.xml}) and with the all-words query
 * ({@code .all.xml}).
 */
class BnfProviderTest {

    private static final List<String> FIXTURES = List.of("le-nom-du-vent", "l-etranger-camus",
            "les-miserables", "dune", "harry-potter", "one-piece", "blue-lock", "demon-slayer",
            "blacksad", "victor-hugo");

    private static String fixture(String name) {
        try (InputStream in = BnfProviderTest.class.getResourceAsStream("/catalog/bnf/" + name + ".xml")) {
            assertNotNull(in, "missing fixture " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Answers each query with what the function returns, and remembers the queries and page size. */
    private static final class StubClient implements BnfClient {

        private final List<String> queries = new ArrayList<>();

        private final List<Integer> pageSizes = new ArrayList<>();

        private final List<String> schemas = new ArrayList<>();

        private final Function<String, Uni<String>> answer;

        private StubClient(Function<String, Uni<String>> answer) {
            this.answer = answer;
        }

        static StubClient always(String xml) {
            return new StubClient(query -> Uni.createFrom().item(xml));
        }

        /** The phrase fixture for the {@code adj} query, the all-words one for the other. */
        static StubClient fixtures(String slug) {
            return new StubClient(query -> Uni.createFrom().item(
                    fixture(slug + (query.contains(" adj ") ? ".phrase" : ".all"))));
        }

        @Override
        public synchronized Uni<String> search(String version, String operation, String query,
                String recordSchema, int maximumRecords) {
            queries.add(query);
            pageSizes.add(maximumRecords);
            schemas.add(recordSchema);
            return answer.apply(query);
        }
    }

    private static BnfProvider provider(BnfClient client, Duration timeout) {
        BnfProvider provider = new BnfProvider();
        provider.client = client;
        provider.callTimeout = timeout;
        return provider;
    }

    private static List<CatalogResult> phrase(String slug) throws Exception {
        return BnfProvider.parse(fixture(slug + ".phrase"));
    }

    private static List<String> queriesFor(CatalogQuery criteria) {
        StubClient client = StubClient.always("");
        provider(client, Duration.ofSeconds(5)).search(criteria, 20);
        return client.queries;
    }

    // --- the requests ---------------------------------------------------------------------

    @Test
    void asksForUnimarc() {
        StubClient client = StubClient.always("");
        provider(client, Duration.ofSeconds(5)).search(CatalogQuery.of("dune"), 20);

        assertEquals(List.of("unimarcxchange", "unimarcxchange"), client.schemas);
    }

    @Test
    void freeTextRunsThePhraseThenTheAllWordsQuery() {
        assertEquals(List.of(
                "bib.title adj \"fourth wing\" and bib.doctype any \"a\"",
                "(bib.title all \"fourth wing\" or bib.author all \"fourth wing\") and bib.doctype any \"a\""),
                queriesFor(CatalogQuery.of("fourth wing")));
    }

    @Test
    void appendsTheAdvancedCriteriaToBothQueries() {
        List<String> queries = queriesFor(new CatalogQuery("dune", "Frank Herbert", null, null, "Pocket", null));

        assertEquals(2, queries.size());
        for (String cql : queries) {
            assertTrue(cql.contains("bib.author all \"Frank Herbert\""), cql);
            assertTrue(cql.contains("bib.publisher all \"Pocket\""), cql);
            assertTrue(cql.endsWith(" and bib.doctype any \"a\""), cql);
        }
        assertTrue(queries.get(0).startsWith("bib.title adj \"dune\" and "), queries.get(0));
        assertTrue(queries.get(1).startsWith("(bib.title all \"dune\" or bib.author all \"dune\") and "),
                queries.get(1));
    }

    @Test
    void aPrefixSearchIsOneTruncatedAllWordsQuery() {
        assertEquals(List.of("bib.title all \"le nom du ven*\" and bib.doctype any \"a\""),
                BnfProvider.cqlQueries(CatalogQuery.of("le nom du ven").withPrefixLastWord()));
    }

    @Test
    void anIsbnSearchIsASingleQuery() {
        assertEquals(List.of("bib.isbn all \"9782266116242\" and bib.doctype any \"a\""),
                queriesFor(new CatalogQuery(null, null, null, null, null, "9782266116242")));
    }

    @Test
    void criteriaWithoutFreeTextAreASingleQuery() {
        assertEquals(List.of("bib.author all \"Frank Herbert\" and bib.doctype any \"a\""),
                queriesFor(new CatalogQuery(null, "Frank Herbert", null, null, null, null)));
    }

    @Test
    void mapsTheLanguageToItsMarcCode() {
        // The BnF indexes languages as MARC codes; the client speaks ISO 639-1.
        assertEquals(List.of("bib.language all \"fre\" and bib.doctype any \"a\""),
                queriesFor(new CatalogQuery(null, null, null, "fr", null, null)));
        // Already a MARC code: passed through rather than dropped.
        assertEquals(List.of("bib.language all \"jpn\" and bib.doctype any \"a\""),
                queriesFor(new CatalogQuery(null, null, null, "jpn", null, null)));
    }

    @Test
    void dropsTheQuotesTypedByTheUser() {
        // An unbalanced quote would make the BnF answer a diagnostic instead of records.
        List<String> queries = queriesFor(new CatalogQuery(null, "Herbert\" or", null, null, null, null));

        assertEquals(List.of("bib.author all \"Herbert or\" and bib.doctype any \"a\""), queries);
        assertFalse(queries.get(0).contains("\"\""), queries.get(0));
    }

    @Test
    void dropsTheReadersVolumeWordsFromTheText() {
        // A BnF title never says "tome": the number is a field of its own.
        assertTrue(queriesFor(CatalogQuery.of("one piece tome 56")).get(0)
                .startsWith("bib.title adj \"one piece 56\""));
    }

    @Test
    void doesNotCallTheBnfForAYearOnItsOwn() {
        // There is nothing to search for: every book printed that year is not an answer.
        StubClient client = StubClient.always("");

        assertTrue(provider(client, Duration.ofSeconds(5))
                .search(new CatalogQuery(null, null, 1965, null, null, null), 20).isEmpty());
        assertTrue(client.queries.isEmpty(), "no criterion the BnF can index, so no call");
    }

    @Test
    void asksForAFullPageWhenTheYearOrTheRankingNeedsOne() {
        StubClient client = StubClient.always("");
        BnfProvider provider = provider(client, Duration.ofSeconds(5));

        provider.search(new CatalogQuery(null, "Frank Herbert", null, null, null, null), 5);
        assertEquals(5, client.pageSizes.get(0), "nothing to filter or rank: ask for what is wanted");

        client.pageSizes.clear();
        provider.search(CatalogQuery.of("dune"), 5);
        assertTrue(client.pageSizes.get(0) > 5, "free text is ranked downstream: a full page");

        client.pageSizes.clear();
        provider.search(new CatalogQuery(null, "Frank Herbert", 1975, null, null, null), 5);
        assertTrue(client.pageSizes.get(0) > 5, "a locally filtered year needs a page to filter");
    }

    // --- Le nom du vent -------------------------------------------------------------------

    @Test
    void readsTheEditionsOfLeNomDuVent() throws Exception {
        List<CatalogResult> results = phrase("le-nom-du-vent");

        assertEquals(6, results.size());
        for (CatalogResult result : results) {
            assertEquals("Le nom du vent", result.title());
            // The translator, Colette Carrière, is not an author: she is 702 $4 730. The
            // illustrator of the anniversary edition (440) follows the author.
            assertTrue(result.authors().startsWith("Patrick Rothfuss"), result.authors());
            assertFalse(result.authors().contains("Carrière"), result.authors());
            assertEquals("Chronique du tueur de roi", result.seriesTitle());
            assertEquals(1, result.volumeNumber());
            assertNotNull(result.pageCount());
            assertEquals("BOOK", result.kind());
            assertEquals("bnf", result.provider());
            assertTrue(result.providerRef().startsWith("ark:/12148/"), result.providerRef());
        }
    }

    @Test
    void emitsTheRelativeCoverOnlyWhenTheRecordHasAFrontCover() throws Exception {
        List<CatalogResult> results = phrase("le-nom-du-vent");

        CatalogResult anniversary = byRef(results, "ark:/12148/cb458523524");
        assertEquals("/api/covers/bnf/cb458523524", anniversary.coverUrl());
        assertEquals("Patrick Rothfuss, Marc Simonetti", anniversary.authors());
        assertEquals(827, anniversary.pageCount());
        assertEquals(2019, anniversary.year());
        assertEquals("Bragelonne", anniversary.publisher());
        assertEquals("Patrick Rothfuss", byRef(results, "ark:/12148/cb43664443h").authors());
        assertEquals("fre", anniversary.language());

        // The Grand livre du mois edition has no 856: no cover, and none guessed.
        assertNull(byRef(results, "ark:/12148/cb43664443h").coverUrl());
    }

    @Test
    void readsTheIdentifiersOfARecord() throws Exception {
        CatalogResult result = byRef(phrase("le-nom-du-vent"), "ark:/12148/cb43664443h");

        // 010 holds the hyphenated ISBN, 073 the EAN: both end as one ISBN-13 without hyphens.
        assertEquals("9782286085117", result.isbn13());
        assertEquals(781, result.pageCount());
        assertEquals(2013, result.year());
        assertEquals("le Grand livre du mois", result.publisher());
    }

    // --- manga and comics -----------------------------------------------------------------

    @Test
    void readsBlueLockAsNumberedManga() throws Exception {
        List<CatalogResult> results = phrase("blue-lock");

        assertFalse(results.isEmpty());
        for (CatalogResult result : results) {
            assertTrue(result.title().matches("Blue [Ll]ock - Tome \\d+"), result.title());
            assertEquals("MANGA", result.kind(), result.title());
            assertNotNull(result.volumeNumber(), result.title());
            assertTrue(result.title().endsWith("Tome " + result.volumeNumber()), result.title());
            assertNotNull(result.seriesTitle());
            assertFalse(result.authors().contains("Lebrun"), "translator credited: " + result.authors());
        }
        CatalogResult volume31 = byRef(results, "ark:/12148/cb48728614g");
        assertEquals("Blue Lock - Tome 31", volume31.title());
        assertEquals("Blue Lock", volume31.seriesTitle());
        assertEquals(31, volume31.volumeNumber());
        assertEquals(192, volume31.pageCount());
        assertEquals("Pika éditions", volume31.publisher());
        assertEquals(2026, volume31.year());
        assertEquals("/api/covers/bnf/cb48728614g", volume31.coverUrl());
    }

    @Test
    void creditsTheScriptwriterThenTheIllustratorOfAManga() throws Exception {
        // 700 $4 070 Kaneshiro, 702 $4 730 Lebrun (translator), 440 Nomura, 410 Berberian.
        CatalogResult volume17 = byRef(phrase("blue-lock"), "ark:/12148/cb473800118");

        assertEquals("Muneyuki Kaneshiro, Yūsuke Nomura", volume17.authors());
    }

    @Test
    void readsOnePieceVolumesAsMangaOrComicNeverBook() throws Exception {
        List<CatalogResult> numbered = phrase("one-piece").stream()
                .filter(result -> result.volumeNumber() != null)
                .toList();

        assertFalse(numbered.isEmpty());
        for (CatalogResult result : numbered) {
            assertTrue(result.kind().equals("MANGA") || result.kind().equals("COMIC"), result.title());
        }
        CatalogResult volume56 = byRef(numbered, "ark:/12148/cb43407362n");
        assertEquals("One Piece - Tome 56", volume56.title());
        assertEquals(56, volume56.volumeNumber());
        assertEquals("Eiichirō Oda", volume56.authors());
        assertEquals("One piece", volume56.seriesTitle());
        CatalogResult volume112 = byRef(numbered, "ark:/12148/cb48756723v");
        assertEquals("One Piece - Tome 112", volume112.title());
        assertEquals(112, volume112.volumeNumber());
        assertEquals("MANGA", volume112.kind());
    }

    @Test
    void dropsTheFilmsAndGamesTheIndexLetsThrough() throws Exception {
        // "One piece Z" is catalogued as "Multimédia multisupport": a DVD box set, not a book.
        assertTrue(phrase("one-piece").stream().noneMatch(result -> result.title().startsWith("One piece Z")));
    }

    @Test
    void readsBlacksadAsAComicCreditingItsAuthors() throws Exception {
        List<CatalogResult> results = phrase("blacksad");

        assertFalse(results.isEmpty());
        for (CatalogResult result : results) {
            assertEquals("COMIC", result.kind(), result.title());
            assertTrue(result.authors().contains("Juan Díaz Canales"), result.authors());
            assertTrue(result.authors().contains("Juanjo Guarnido"), result.authors());
            assertFalse(result.authors().contains("Ruiz"), "translator credited: " + result.authors());
        }
        // Part number and part name: "[5]" is a plain 5, and $i follows the number.
        CatalogResult amarillo = byRef(results, "ark:/12148/cb437848703");
        assertEquals("Blacksad - Tome 5 : Amarillo", amarillo.title());
        assertEquals(5, amarillo.volumeNumber());
        assertEquals("Blacksad", amarillo.seriesTitle());
        // A part carried by the series statement only: the title proper stays alone.
        CatalogResult standalone = byRef(results, "ark:/12148/cb437056379");
        assertEquals("Amarillo", standalone.title());
        assertEquals("Blacksad", standalone.seriesTitle());
        assertEquals(5, standalone.volumeNumber());
    }

    @Test
    void doesNotTakeAPublisherCollectionForASeries() throws Exception {
        // 225 "Pika Shônen" carries no volume: it is a shelf, and the series is the title.
        CatalogResult result = byRef(phrase("blue-lock"), "ark:/12148/cb48862223w");

        assertEquals("Blue Lock", result.seriesTitle());
        assertEquals(35, result.volumeNumber());
    }

    // --- every fixture --------------------------------------------------------------------

    @Test
    void neverGuessesACoverFromOpenLibrary() throws Exception {
        for (String slug : FIXTURES) {
            for (String kind : List.of("phrase", "all")) {
                for (CatalogResult result : BnfProvider.parse(fixture(slug + "." + kind))) {
                    String cover = result.coverUrl();
                    assertTrue(cover == null || cover.startsWith("/api/covers/bnf/cb"), slug + ": " + cover);
                }
            }
        }
    }

    @Test
    void everyFixtureYieldsCleanResults() throws Exception {
        for (String slug : FIXTURES) {
            for (String kind : List.of("phrase", "all")) {
                List<CatalogResult> results = BnfProvider.parse(fixture(slug + "." + kind));
                assertFalse(results.isEmpty(), slug + "." + kind);
                for (CatalogResult result : results) {
                    String where = slug + "." + kind + ": " + result.title();
                    assertEquals(result.title().strip().replaceAll("\\s+", " "), result.title(), where);
                    assertFalse(result.title().matches(".*[/:;,]$"), where);
                    assertFalse(result.title().contains("L' "), where);
                    assertTrue(result.isbn13() == null || result.isbn13().matches("97[89]\\d{10}"), where);
                    assertTrue(result.authors() == null || !result.authors().toLowerCase().contains("traduct"), where);
                }
            }
        }
    }

    // --- merging --------------------------------------------------------------------------

    @Test
    void putsThePhraseResultsFirstAndListsAnArkOnce() {
        List<CatalogResult> results = provider(StubClient.fixtures("le-nom-du-vent"), Duration.ofSeconds(5))
                .search(CatalogQuery.of("le nom du vent"), 20);

        List<String> refs = results.stream().map(CatalogResult::providerRef).toList();
        assertEquals(refs.size(), refs.stream().distinct().count(), "an ark appears twice: " + refs);

        List<String> phraseRefs = parsedRefs("le-nom-du-vent.phrase");
        assertEquals(phraseRefs, refs.subList(0, phraseRefs.size()), "the exact phrase comes first");
        List<String> allRefs = parsedRefs("le-nom-du-vent.all");
        assertTrue(allRefs.stream().anyMatch(ref -> !phraseRefs.contains(ref)), "fixture adds nothing to merge");
        assertTrue(refs.containsAll(allRefs), "an all-words result was lost");
    }

    @Test
    void keepsTheAllWordsResultsWhenThePhraseCallFails() {
        StubClient client = new StubClient(query -> query.contains(" adj ")
                ? Uni.createFrom().failure(new IllegalStateException("connection reset"))
                : Uni.createFrom().item(fixture("le-nom-du-vent.all")));

        List<CatalogResult> results = provider(client, Duration.ofSeconds(5))
                .search(CatalogQuery.of("le nom du vent"), 20);

        assertEquals(parsedRefs("le-nom-du-vent.all"), results.stream().map(CatalogResult::providerRef).toList());
    }

    @Test
    void keepsThePhraseResultsWhenTheAllWordsAnswerIsGarbage() {
        StubClient client = new StubClient(query -> Uni.createFrom().item(query.contains(" adj ")
                ? fixture("le-nom-du-vent.phrase") : "<html>502 Bad Gateway</html>"));

        List<CatalogResult> results = provider(client, Duration.ofSeconds(5))
                .search(CatalogQuery.of("le nom du vent"), 20);

        assertEquals(parsedRefs("le-nom-du-vent.phrase"), results.stream().map(CatalogResult::providerRef).toList());
    }

    @Test
    void keepsThePhraseResultsWhenTheAllWordsCallNeverAnswers() {
        StubClient client = new StubClient(query -> query.contains(" adj ")
                ? Uni.createFrom().item(fixture("le-nom-du-vent.phrase"))
                : Uni.createFrom().nothing());

        long start = System.nanoTime();
        List<CatalogResult> results = provider(client, Duration.ofMillis(300))
                .search(CatalogQuery.of("le nom du vent"), 20);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertEquals(parsedRefs("le-nom-du-vent.phrase"), results.stream().map(CatalogResult::providerRef).toList());
        assertTrue(elapsedMs < 5_000, "took " + elapsedMs + "ms");
    }

    @Test
    void filtersTheYearOnTheRecordsThemselves() {
        List<CatalogResult> results = provider(StubClient.fixtures("le-nom-du-vent"), Duration.ofSeconds(5))
                .search(new CatalogQuery("le nom du vent", null, 2019, null, null, null), 20);

        assertFalse(results.isEmpty());
        assertTrue(results.stream().allMatch(result -> Integer.valueOf(2019).equals(result.year())));
    }

    // --- failures -------------------------------------------------------------------------

    @Test
    void degradesToNoResultWhenTheAnswerIsNotTheExpectedXml() {
        // A gateway error page instead of an SRU envelope must not reach CatalogService as an
        // exception: the aggregate has to go on with the other book provider's results.
        for (String answer : List.of("<html><body>502 Bad Gateway</body></html>", "not xml at all", "")) {
            assertTrue(provider(StubClient.always(answer), Duration.ofSeconds(5))
                    .search(CatalogQuery.of("dune"), 20).isEmpty());
        }
    }

    @Test
    void degradesToNoResultWhenTheProviderIsUnreachable() {
        StubClient client = new StubClient(
                query -> Uni.createFrom().failure(new IllegalStateException("connection refused")));

        assertTrue(provider(client, Duration.ofSeconds(5)).search(CatalogQuery.of("dune"), 20).isEmpty());
    }

    @Test
    void degradesToNoResultWhenTheClientThrows() {
        BnfClient client = (version, operation, query, schema, max) -> {
            throw new IllegalStateException("no connection available");
        };

        assertTrue(provider(client, Duration.ofSeconds(5)).search(CatalogQuery.of("dune"), 20).isEmpty());
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
                    <mxc:record xmlns:mxc="info:lc/xmlns/marcxchange-v2" id="ark:/12148/cb1">
                      <mxc:datafield tag="200"><mxc:subfield code="a">&lol;</mxc:subfield></mxc:datafield>
                    </mxc:record>
                  </srw:recordData></srw:record></srw:records>
                </srw:searchRetrieveResponse>
                """;

        assertTrue(provider(StubClient.always(bomb), Duration.ofSeconds(5))
                .search(CatalogQuery.of("dune"), 20).isEmpty());
    }

    // --- small cases ----------------------------------------------------------------------

    private static String record(String ark, String fields) {
        return "<srw:searchRetrieveResponse xmlns:srw=\"http://www.loc.gov/zing/srw/\"><srw:records>"
                + "<srw:record><srw:recordData><mxc:record xmlns:mxc=\"info:lc/xmlns/marcxchange-v2\" id=\""
                + ark + "\">" + fields + "</mxc:record></srw:recordData></srw:record>"
                + "</srw:records></srw:searchRetrieveResponse>";
    }

    private static String field(String tag, String... subfields) {
        StringBuilder xml = new StringBuilder("<mxc:datafield tag=\"" + tag + "\">");
        for (int i = 0; i < subfields.length; i += 2) {
            xml.append("<mxc:subfield code=\"").append(subfields[i]).append("\">")
                    .append(subfields[i + 1]).append("</mxc:subfield>");
        }
        return xml.append("</mxc:datafield>").toString();
    }

    @Test
    void cleansTheTitle() throws Exception {
        String xml = record("ark:/12148/cb1", field("200", "a", "L'  Etranger /", "h", "T.. 3",
                "i", "Les &amp; autres :"));

        CatalogResult result = BnfProvider.parse(xml).get(0);

        // Elision spacing, trailing ISBD punctuation, entities and the part number.
        assertEquals("L'Etranger - Tome 3 : Les & autres", result.title());
        assertEquals(3, result.volumeNumber());
    }

    @Test
    void keepsAnUnnumberedPartAsItIsWritten() throws Exception {
        CatalogResult result = BnfProvider.parse(record("ark:/12148/cb1",
                field("200", "a", "Dune", "h", "Partie spéciale"))).get(0);

        assertEquals("Dune - Partie spéciale", result.title());
        assertNull(result.volumeNumber());
    }

    @Test
    void keepsPersonsWithoutRelatorCodesExceptNamedTranslators() throws Exception {
        String xml = record("ark:/12148/cb1", field("200", "a", "Dune")
                + field("700", "a", "Herbert", "b", "Frank")
                + field("702", "a", "Demuth", "b", "Michel", "c", "Traducteur"));

        assertEquals("Frank Herbert", BnfProvider.parse(xml).get(0).authors());
    }

    @Test
    void readsAnOldRecordsIsbn10AsIsbn13() throws Exception {
        String xml = record("ark:/12148/cb1", field("200", "a", "Dune") + field("010", "a", "2-7242-1298-3"));

        assertEquals("9782724212983", BnfProvider.parse(xml).get(0).isbn13());
    }

    @Test
    void readsTheYearOutOfAWordedDateAndLeavesAnEstimatedPageCountOut() throws Exception {
        String xml = record("ark:/12148/cb1", field("200", "a", "Dune")
                + field("210", "c", "Pocket", "d", "cop. 1975-")
                + field("215", "a", "1 vol. (non paginé [ca 208] p.)"));

        CatalogResult result = BnfProvider.parse(xml).get(0);

        assertEquals(1975, result.year());
        assertNull(result.pageCount());
    }

    @Test
    void readsAMangaFromItsPublisherAndFromAnAccentedCollection() throws Exception {
        assertEquals("MANGA", BnfProvider.parse(record("ark:/12148/cb1",
                field("200", "a", "Berserk") + field("214", "c", "Glénat manga"))).get(0).kind());
        assertEquals("MANGA", BnfProvider.parse(record("ark:/12148/cb1",
                field("200", "a", "Berserk") + field("225", "a", "Collection Shōnen"))).get(0).kind());
        // An original title and a Japanese author do not make a manga.
        assertEquals("BOOK", BnfProvider.parse(record("ark:/12148/cb1",
                field("200", "a", "Kafka sur le rivage") + field("454", "t", "Umibe no Kafuka")
                        + field("700", "a", "Murakami", "b", "Haruki", "4", "070"))).get(0).kind());
    }

    @Test
    void ignoresARecordWithoutATitle() throws Exception {
        assertTrue(BnfProvider.parse(record("ark:/12148/cb1", field("210", "d", "1965"))).isEmpty());
    }

    private static CatalogResult byRef(List<CatalogResult> results, String ark) {
        return results.stream()
                .filter(result -> ark.equals(result.providerRef()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(ark + " not in " + results.stream()
                        .map(CatalogResult::providerRef).toList()));
    }

    private static List<String> parsedRefs(String fixtureName) {
        try {
            return BnfProvider.parse(fixture(fixtureName)).stream().map(CatalogResult::providerRef).toList();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
