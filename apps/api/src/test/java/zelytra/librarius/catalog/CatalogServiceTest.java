package zelytra.librarius.catalog;

import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.Kind;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests routing and aggregation, without CDI or network (fake providers). */
class CatalogServiceTest {

    private static CatalogResult result(String kind, String title) {
        return result(kind, title, null);
    }

    private static CatalogResult result(String kind, String title, String author) {
        return new CatalogResult(kind, title, author, null, null, null, null, null, null, null,
                "fake", "ref");
    }

    private record FakeProvider(Kind kind, CatalogResult canned) implements CatalogProvider {
        @Override
        public String name() {
            return "fake-" + kind;
        }

        @Override
        public List<CatalogResult> search(CatalogQuery query, int limit) {
            return List.of(canned);
        }

        @Override
        public List<CatalogResult> upcoming(int limit) {
            return List.of(canned);
        }
    }

    /** Answers with as many distinct, tagged results as the caller asks for. */
    private record VerboseProvider(String name, Kind kind, int available)
            implements CatalogProvider {
        @Override
        public List<CatalogResult> search(CatalogQuery query, int limit) {
            return titled(Math.min(available, limit));
        }

        @Override
        public List<CatalogResult> upcoming(int limit) {
            return titled(Math.min(available, limit));
        }

        private List<CatalogResult> titled(int count) {
            List<CatalogResult> results = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) {
                results.add(result("BOOK", name + "-title-" + i));
            }
            return results;
        }
    }

    /** Answers with a fixed, ordered list — lets a test control rank and content exactly. */
    private record ListProvider(Kind kind, List<CatalogResult> canned) implements CatalogProvider {
        @Override
        public String name() {
            return "list-" + kind;
        }

        @Override
        public List<CatalogResult> search(CatalogQuery query, int limit) {
            return canned.stream().limit(limit).toList();
        }

        @Override
        public List<CatalogResult> upcoming(int limit) {
            return canned;
        }
    }

    /** Keeps the criteria it was handed, to check they survive the trip to the provider. */
    private static final class RecordingProvider implements CatalogProvider {

        private CatalogQuery received;

        @Override
        public String name() {
            return "recording";
        }

        @Override
        public Kind kind() {
            return Kind.BOOK;
        }

        @Override
        public List<CatalogResult> search(CatalogQuery query, int limit) {
            received = query;
            return List.of();
        }

        @Override
        public List<CatalogResult> upcoming(int limit) {
            return List.of();
        }
    }

    /**
     * Pass-through cache: this test covers routing and merging, and wiring the real two-level
     * cache in would drag a database into a test that needs neither CDI nor network.
     */
    private static CatalogCache passThroughCache() {
        return new CatalogCache() {
            @Override
            public List<CatalogResult> get(Scope scope, String provider, String key,
                    Supplier<List<CatalogResult>> loader) {
                return loader.get();
            }
        };
    }

    @Test
    void routesToTheProviderMatchingTheKind() {
        CatalogService service = new CatalogService(List.of(
                new FakeProvider(Kind.BOOK, result("BOOK", "Fourth Wing")),
                new FakeProvider(Kind.MANGA, result("MANGA", "One Piece"))),
                passThroughCache());

        assertEquals("Fourth Wing",
                service.search(Set.of(Kind.BOOK), CatalogQuery.of("wing"), 10).get(0).title());
        assertEquals("One Piece",
                service.search(Set.of(Kind.MANGA), CatalogQuery.of("piece"), 10).get(0).title());
    }

    @Test
    void returnsEmptyWhenNoProviderForKind() {
        CatalogService service = new CatalogService(List.of(
                new FakeProvider(Kind.BOOK, result("BOOK", "Fourth Wing"))),
                passThroughCache());

        assertTrue(service.search(Set.of(Kind.MANGA), CatalogQuery.of("piece"), 10).isEmpty());
    }

    @Test
    void searchesEveryRegisteredKindWhenNoneIsNamed() {
        // No kind at all: one call must reach every registered provider, across mediums, and
        // return their answers merged into a single list.
        CatalogService service = new CatalogService(List.of(
                new FakeProvider(Kind.BOOK, result("BOOK", "Fourth Wing")),
                new FakeProvider(Kind.MANGA, result("MANGA", "One Piece"))),
                passThroughCache());

        List<String> titles = service.search(Set.of(), CatalogQuery.of("anything"), 10).stream()
                .map(CatalogResult::title).toList();

        assertTrue(titles.contains("Fourth Wing"), titles.toString());
        assertTrue(titles.contains("One Piece"), titles.toString());
    }

    @Test
    void narrowsToTheNamedKindsWhenSeveralAreGiven() {
        // Two of three mediums named: the third provider is left out entirely.
        CatalogService service = new CatalogService(List.of(
                new FakeProvider(Kind.BOOK, result("BOOK", "Fourth Wing")),
                new FakeProvider(Kind.MANGA, result("MANGA", "One Piece")),
                new FakeProvider(Kind.COMIC, result("COMIC", "Watchmen"))),
                passThroughCache());

        List<String> titles = service
                .search(Set.of(Kind.BOOK, Kind.MANGA), CatalogQuery.of("anything"), 10).stream()
                .map(CatalogResult::title).toList();

        assertEquals(Set.of("Fourth Wing", "One Piece"), new HashSet<>(titles));
    }

    @Test
    void aProviderBeingDownDegradesToTheOthersInACrossMediumSearch() {
        // No kind named, so every provider is reached; one returning nothing (down, or
        // off-topic) costs only its own results, the rest of the mediums still surface.
        CatalogService service = new CatalogService(List.of(
                new VerboseProvider("openlibrary", Kind.BOOK, 0),
                new FakeProvider(Kind.MANGA, result("MANGA", "One Piece"))),
                passThroughCache());

        List<String> titles = service.search(Set.of(), CatalogQuery.of("anything"), 10).stream()
                .map(CatalogResult::title).toList();

        assertEquals(List.of("One Piece"), titles);
    }

    @Test
    void aggregatesMultipleProvidersForSameKindAndDeduplicates() {
        // Two book providers returning the same title.
        CatalogService service = new CatalogService(List.of(
                new FakeProvider(Kind.BOOK, result("BOOK", "Fourth Wing")),
                new FakeProvider(Kind.BOOK, result("BOOK", "Fourth Wing"))),
                passThroughCache());

        // The duplicate (same title/author) is merged into a single entry.
        assertEquals(1, service.search(Set.of(Kind.BOOK), CatalogQuery.of("wing"), 10).size());
    }

    @Test
    void givesEachProviderAFairShareOfTheLimitWhenBothReturnAFullPage() {
        // Both providers could each fill the whole limit on their own; the faster/more
        // verbose one must not crowd the other out of the merged page.
        CatalogService service = new CatalogService(List.of(
                new VerboseProvider("openlibrary", Kind.BOOK, 20),
                new VerboseProvider("bnf", Kind.BOOK, 20)),
                passThroughCache());

        List<CatalogResult> results = service.search(Set.of(Kind.BOOK), CatalogQuery.of("wing"), 10);

        assertEquals(10, results.size());
        long fromOpenLibrary = results.stream()
                .filter(r -> r.title().startsWith("openlibrary")).count();
        long fromBnf = results.stream().filter(r -> r.title().startsWith("bnf")).count();
        assertEquals(5, fromOpenLibrary);
        assertEquals(5, fromBnf);
    }

    @Test
    void aSilentProviderLeavesTheWholeLimitToTheOther() {
        // A provider returning nothing (down, or off-topic) must not halve what the
        // other one is allowed to contribute.
        CatalogService service = new CatalogService(List.of(
                new VerboseProvider("openlibrary", Kind.BOOK, 0),
                new VerboseProvider("bnf", Kind.BOOK, 20)),
                passThroughCache());

        List<CatalogResult> results = service.search(Set.of(Kind.BOOK), CatalogQuery.of("wing"), 10);

        assertEquals(10, results.size());
        assertTrue(results.stream().allMatch(r -> r.title().startsWith("bnf")));
    }

    @Test
    void handsEveryAdvancedCriterionToTheProvider() {
        RecordingProvider provider = new RecordingProvider();
        CatalogService service = new CatalogService(List.of(provider), passThroughCache());
        CatalogQuery query = new CatalogQuery("dune", "herbert", 1965, "fr", "pocket", null);

        service.search(Set.of(Kind.BOOK), query, 10);

        // The service aggregates and caches; narrowing is the provider's job, and it can
        // only do it if the criteria reach it untouched.
        assertEquals(query, provider.received);
    }

    @Test
    void doesNotShareACacheEntryBetweenTwoDifferentCriteria() {
        Set<String> keys = new HashSet<>();
        CatalogCache recordingCache = new CatalogCache() {
            @Override
            public List<CatalogResult> get(Scope scope, String provider, String key,
                    Supplier<List<CatalogResult>> loader) {
                keys.add(key);
                return loader.get();
            }
        };
        CatalogService service = new CatalogService(
                List.of(new FakeProvider(Kind.BOOK, result("BOOK", "Dune"))), recordingCache);

        service.search(Set.of(Kind.BOOK), CatalogQuery.of("dune"), 10);
        service.search(Set.of(Kind.BOOK), new CatalogQuery("dune", "herbert", null, null, null, null), 10);

        // Same text, one extra criterion: the second search must not be served the first
        // one's answer.
        assertEquals(2, keys.size());
    }

    @Test
    void deduplicatesAcrossAccentAndPunctuationVariants() {
        // The same title and author from two catalogues, spelled with different accents and
        // punctuation, must merge into a single entry rather than read as a duplicate.
        CatalogService service = new CatalogService(List.of(
                new ListProvider(Kind.BOOK, List.of(
                        result("BOOK", "Astérix — Tome 1", "Goscinny"),
                        result("BOOK", "Asterix, tome 1", "Goscinny")))),
                passThroughCache());

        assertEquals(1, service.search(Set.of(Kind.BOOK), CatalogQuery.of("asterix"), 10).size());
    }

    @Test
    void ranksResultsMatchingTheQueryAboveOnesThatDoNot() {
        // A provider that returns in its own, non-relevance order: the genuine match must
        // still surface first once the merge re-ranks the page on the query.
        CatalogService service = new CatalogService(List.of(
                new ListProvider(Kind.BOOK, List.of(
                        result("BOOK", "An Unrelated Title"),
                        result("BOOK", "The Hobbit")))),
                passThroughCache());

        List<String> titles = service.search(Set.of(Kind.BOOK), CatalogQuery.of("hobbit"), 10)
                .stream().map(CatalogResult::title).toList();

        assertEquals("The Hobbit", titles.get(0));
    }
}
