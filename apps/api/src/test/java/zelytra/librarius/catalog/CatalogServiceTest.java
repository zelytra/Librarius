package zelytra.librarius.catalog;

import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.Kind;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests routing and aggregation, without CDI or network (fake providers). */
class CatalogServiceTest {

    private static CatalogResult result(String kind, String title) {
        return new CatalogResult(kind, title, null, null, null, null, null, null, null, null,
                "fake", "ref");
    }

    private record FakeProvider(Kind kind, CatalogResult canned) implements CatalogProvider {
        @Override
        public String name() {
            return "fake-" + kind;
        }

        @Override
        public List<CatalogResult> search(String query, int limit) {
            return List.of(canned);
        }

        @Override
        public List<CatalogResult> upcoming(int limit) {
            return List.of(canned);
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

        assertEquals("Fourth Wing", service.search(Kind.BOOK, "wing", 10).get(0).title());
        assertEquals("One Piece", service.search(Kind.MANGA, "piece", 10).get(0).title());
    }

    @Test
    void returnsEmptyWhenNoProviderForKind() {
        CatalogService service = new CatalogService(List.of(
                new FakeProvider(Kind.BOOK, result("BOOK", "Fourth Wing"))),
                passThroughCache());

        assertTrue(service.search(Kind.MANGA, "piece", 10).isEmpty());
    }

    @Test
    void aggregatesMultipleProvidersForSameKindAndDeduplicates() {
        // Two book providers returning the same title.
        CatalogService service = new CatalogService(List.of(
                new FakeProvider(Kind.BOOK, result("BOOK", "Fourth Wing")),
                new FakeProvider(Kind.BOOK, result("BOOK", "Fourth Wing"))),
                passThroughCache());

        // The duplicate (same title/author) is merged into a single entry.
        assertEquals(1, service.search(Kind.BOOK, "wing", 10).size());
    }
}
