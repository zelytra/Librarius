package zelytra.librarius.catalog.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import zelytra.librarius.catalog.CatalogQuery;
import zelytra.librarius.catalog.CatalogResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the Solr query the provider builds, without going out to Open Library. */
class OpenLibraryProviderTest {

    /** Captures the query the provider sends, and answers nothing. */
    private static final class CapturingClient implements OpenLibraryClient {

        private String q;

        @Override
        public Uni<SearchResponse> search(String q, int limit, String fields, String sort, String lang) {
            this.q = q;
            return Uni.createFrom().item(new SearchResponse(List.of()));
        }

        @Override
        public Uni<EditionsResponse> editions(String ref, int limit) {
            return Uni.createFrom().item(new EditionsResponse(List.of()));
        }
    }

    /** Answers a fixed set of edition records, so the mapping can be checked in isolation. */
    private static OpenLibraryClient editionsClient(OpenLibraryClient.EditionEntry... entries) {
        return new OpenLibraryClient() {
            @Override
            public Uni<SearchResponse> search(String q, int limit, String fields, String sort, String lang) {
                return Uni.createFrom().item(new SearchResponse(List.of()));
            }

            @Override
            public Uni<EditionsResponse> editions(String ref, int limit) {
                return Uni.createFrom().item(new EditionsResponse(List.of(entries)));
            }
        };
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
        OpenLibraryProvider provider = provider(new OpenLibraryClient() {
            @Override
            public Uni<SearchResponse> search(String q, int limit, String fields, String sort, String lang) {
                return Uni.createFrom().nothing();
            }

            @Override
            public Uni<EditionsResponse> editions(String ref, int limit) {
                return Uni.createFrom().nothing();
            }
        }, Duration.ofMillis(200));

        long start = System.nanoTime();
        List<?> results = provider.search(CatalogQuery.of("never answers"), 20);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(results.isEmpty(), "a timed-out call degrades into an empty result");
        assertTrue(elapsedMs < 5_000, "the call must give up on its own, took " + elapsedMs + "ms");
    }

    // ── Editions ─────────────────────────────────────────────────────────────────

    private static OpenLibraryClient.EditionEntry entry(String key, String isbn13,
            String publisher, String languageKey, Long cover) {
        return new OpenLibraryClient.EditionEntry(key,
                isbn13 == null ? null : List.of(isbn13), null,
                publisher == null ? null : List.of(publisher),
                languageKey == null ? null : List.of(new OpenLibraryClient.LanguageRef(languageKey)),
                cover == null ? null : List.of(cover), null);
    }

    @Test
    void mapsEachEditionRecordToOnePrinting() {
        OpenLibraryProvider provider = provider(editionsClient(
                entry("/books/OL111M", "9780575081406", "Gollancz", "/languages/eng", 42L)),
                Duration.ofSeconds(5));

        CatalogResult edition = provider.editionsOf("OL45804W", 20).get(0);

        assertEquals("openlibrary", edition.provider());
        assertEquals("OL111M", edition.providerRef());
        assertEquals("9780575081406", edition.isbn13());
        assertEquals("Gollancz", edition.publisher());
        assertEquals("eng", edition.language());
        assertEquals("https://covers.openlibrary.org/b/id/42-M.jpg", edition.coverUrl());
        // The edition list carries no title of its own: the work names the title, the row a
        // printing of it.
        assertNull(edition.title());
    }

    @Test
    void dropsAnEditionThatCarriesNeitherIsbnNorCover() {
        OpenLibraryProvider provider = provider(editionsClient(
                entry("/books/OL1M", null, "No ISBN Press", "/languages/eng", null),
                entry("/books/OL2M", "9780575081406", "Gollancz", "/languages/eng", 42L)),
                Duration.ofSeconds(5));

        List<CatalogResult> editions = provider.editionsOf("OL45804W", 20);

        // The record with nothing to show or deduplicate on is left out, not listed empty.
        assertEquals(1, editions.size());
        assertEquals("Gollancz", editions.get(0).publisher());
    }

    @Test
    void answersNothingForABlankReference() {
        OpenLibraryProvider provider = provider(client, Duration.ofSeconds(5));

        assertTrue(provider.editionsOf("  ", 20).isEmpty());
        assertTrue(provider.editionsOf(null, 20).isEmpty());
    }

    @Test
    void searchResultsCarryTheWorkReferenceTheEditionsLookupKeysOn() {
        OpenLibraryClient answering = new OpenLibraryClient() {
            @Override
            public Uni<SearchResponse> search(String q, int limit, String fields, String sort, String lang) {
                assertTrue(fields.startsWith("key,"), fields);
                return Uni.createFrom().item(new SearchResponse(List.of(new Doc(
                        "/works/OL15505243W", "La Horde du Contrevent", List.of("Alain Damasio"),
                        2004, 6670450L, List.of("9782070342266"), List.of("Gallimard"),
                        List.of("fre"), 700, 40, null))));
            }

            @Override
            public Uni<EditionsResponse> editions(String ref, int limit) {
                return Uni.createFrom().item(new EditionsResponse(List.of()));
            }
        };

        CatalogResult result = provider(answering, Duration.ofSeconds(5))
                .search(CatalogQuery.of("la horde du contrevent"), 10).get(0);

        assertEquals("openlibrary", result.provider());
        assertEquals("OL15505243W", result.providerRef());
    }

    @Test
    void readsOnlyAWellFormedWorkKey() {
        assertEquals("OL45804W", OpenLibraryProvider.workKey("/works/OL45804W"));
        assertNull(OpenLibraryProvider.workKey("/books/OL7353617M"));
        assertNull(OpenLibraryProvider.workKey("/works/../etc"));
        assertNull(OpenLibraryProvider.workKey(null));
    }

    // ── French-first, work-centric results ───────────────────────────────────────

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** A client answering what Open Library really answered, recorded in the fixtures. */
    private static OpenLibraryClient recorded(String fixture) {
        return answering(() -> {
            try (InputStream in = OpenLibraryProviderTest.class
                    .getResourceAsStream("/catalog/openlibrary/" + fixture + ".json")) {
                return Uni.createFrom().item(MAPPER.readValue(in, OpenLibraryClient.SearchResponse.class));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static OpenLibraryClient answering(
            Supplier<Uni<OpenLibraryClient.SearchResponse>> answer) {
        return new OpenLibraryClient() {
            @Override
            public Uni<SearchResponse> search(String q, int limit, String fields, String sort, String lang) {
                return answer.get();
            }

            @Override
            public Uni<EditionsResponse> editions(String ref, int limit) {
                return Uni.createFrom().item(new EditionsResponse(List.of()));
            }
        };
    }

    private List<CatalogResult> searchFixture(String fixture) {
        return provider(recorded(fixture), Duration.ofSeconds(5))
                .search(CatalogQuery.of(fixture), 40);
    }

    @Test
    void harryPotterLeadsWithItsFrenchEdition() {
        List<CatalogResult> results = searchFixture("harry-potter");
        CatalogResult first = results.get(0);

        // The fixture spells the accents as a letter plus a combining mark (NFD), as recorded.
        assertEquals("Harry Potter à l'école des sorciers", first.title());
        assertEquals("fre", first.language());
        assertTrue(first.coverUrl().contains("/b/id/"), first.coverUrl());
        assertTrue(first.popularity() > 100, "popularity is the work's edition count");
    }

    @Test
    void leNomDuVentIsFoundByItsFrenchTitle() {
        CatalogResult result = searchFixture("le-nom-du-vent").stream()
                .filter(r -> "Le Nom du Vent".equals(r.title())
                        && r.authors() != null && r.authors().contains("Patrick Rothfuss"))
                .findFirst().orElseThrow();

        assertNotNull(result.coverUrl());
    }

    @Test
    void noSearchResultBuildsAnIsbnCoverUrl() {
        // /b/isbn/ covers 404 most of the time; a cover is a cover id or nothing.
        for (String fixture : List.of("dune", "harry-potter", "le-nom-du-vent", "l-etranger-camus",
                "les-miserables", "one-piece", "blue-lock", "demon-slayer", "blacksad", "victor-hugo")) {
            for (CatalogResult r : searchFixture(fixture)) {
                assertTrue(r.coverUrl() == null || !r.coverUrl().contains("/b/isbn/"),
                        fixture + ": " + r.coverUrl());
            }
        }
    }

    @Test
    void aWorkWithoutEditionsMapsFromItsOwnFields() {
        OpenLibraryClient.Doc doc = new OpenLibraryClient.Doc("/works/OL1W", "Dune",
                List.of("Frank Herbert"), 1965, 99L, List.of("0441013597", "9780441013593"),
                List.of("Ace"), List.of("eng"), 600, 250, null);
        OpenLibraryProvider provider = provider(answering(() -> Uni.createFrom()
                .item(new OpenLibraryClient.SearchResponse(List.of(doc)))), Duration.ofSeconds(5));

        CatalogResult r = provider.search(CatalogQuery.of("dune"), 10).get(0);

        assertEquals("Dune", r.title());
        assertEquals("eng", r.language());
        assertEquals("9780441013593", r.isbn13());
        assertEquals("Ace", r.publisher());
        assertEquals(1965, r.year());
        assertEquals("https://covers.openlibrary.org/b/id/99-M.jpg", r.coverUrl());
        assertEquals(250, r.popularity());
    }

    @Test
    void aSparseEditionFallsBackToTheWorkFieldByField() {
        OpenLibraryClient.EditionDoc edition = new OpenLibraryClient.EditionDoc("/books/OL2M",
                "Le Nom du Vent", List.of("fre"), null, null, null);
        OpenLibraryClient.Doc doc = new OpenLibraryClient.Doc("/works/OL3W", "The Name of the Wind",
                List.of("Patrick Rothfuss"), 2007, 7L, List.of("9780756404741"), List.of("DAW"),
                List.of("eng", "fre"), 700, 76,
                new OpenLibraryClient.EditionHits(List.of(edition)));
        OpenLibraryProvider provider = provider(answering(() -> Uni.createFrom()
                .item(new OpenLibraryClient.SearchResponse(List.of(doc)))), Duration.ofSeconds(5));

        CatalogResult r = provider.search(CatalogQuery.of("nom du vent"), 10).get(0);

        assertEquals("Le Nom du Vent", r.title());
        assertEquals("fre", r.language());
        // Nothing on the edition for these: the work's own values stand in.
        assertEquals("9780756404741", r.isbn13());
        assertEquals("DAW", r.publisher());
        assertEquals("https://covers.openlibrary.org/b/id/7-M.jpg", r.coverUrl());
    }

    @Test
    void retriesOnceWhenTheFirstCallFails() {
        AtomicInteger calls = new AtomicInteger();
        OpenLibraryClient.Doc doc = new OpenLibraryClient.Doc("/works/OL1W", "Dune", null, null,
                null, null, null, null, null, null, null);
        OpenLibraryProvider provider = provider(answering(() -> calls.incrementAndGet() == 1
                ? Uni.createFrom().failure(new IllegalStateException("not json"))
                : Uni.createFrom().item(new OpenLibraryClient.SearchResponse(List.of(doc)))),
                Duration.ofSeconds(5));

        assertEquals(1, provider.search(CatalogQuery.of("dune"), 10).size());
        assertEquals(2, calls.get());
    }

    @Test
    void retriesOnceWhenTheAnswerIsEmpty() {
        AtomicInteger calls = new AtomicInteger();
        OpenLibraryClient.Doc doc = new OpenLibraryClient.Doc("/works/OL1W", "Dune", null, null,
                null, null, null, null, null, null, null);
        OpenLibraryProvider provider = provider(answering(() -> calls.incrementAndGet() == 1
                ? Uni.createFrom().nullItem()
                : Uni.createFrom().item(new OpenLibraryClient.SearchResponse(List.of(doc)))),
                Duration.ofSeconds(5));

        assertEquals(1, provider.search(CatalogQuery.of("dune"), 10).size());
    }

    @Test
    void givesUpAfterTheSecondFailure() {
        AtomicInteger calls = new AtomicInteger();
        OpenLibraryProvider provider = provider(answering(() -> {
            calls.incrementAndGet();
            return Uni.createFrom().failure(new IllegalStateException("down"));
        }), Duration.ofSeconds(5));

        assertTrue(provider.search(CatalogQuery.of("dune"), 10).isEmpty());
        assertEquals(2, calls.get());
    }

    @Test
    void doesNotRetryALegitimatelyEmptyAnswer() {
        AtomicInteger calls = new AtomicInteger();
        OpenLibraryProvider provider = provider(answering(() -> {
            calls.incrementAndGet();
            return Uni.createFrom().item(new OpenLibraryClient.SearchResponse(List.of()));
        }), Duration.ofSeconds(5));

        assertTrue(provider.search(CatalogQuery.of("zzzz"), 10).isEmpty());
        assertEquals(1, calls.get());
    }
}
