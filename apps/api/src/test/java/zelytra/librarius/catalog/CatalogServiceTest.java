package zelytra.librarius.catalog;

import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.Kind;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Teste le routage et l'agrégation, sans CDI ni réseau (fournisseurs factices). */
class CatalogServiceTest {

    private static CatalogResult result(String kind, String title) {
        return new CatalogResult(kind, title, null, null, null, null, null, null, null, null,
                "fake", "ref");
    }

    private record FakeProvider(Kind kind, CatalogResult canned) implements CatalogProvider {
        @Override
        public List<CatalogResult> search(String query, int limit) {
            return List.of(canned);
        }

        @Override
        public List<CatalogResult> upcoming(int limit) {
            return List.of(canned);
        }
    }

    @Test
    void routesToTheProviderMatchingTheKind() {
        CatalogService service = new CatalogService(List.of(
                new FakeProvider(Kind.BOOK, result("BOOK", "Fourth Wing")),
                new FakeProvider(Kind.MANGA, result("MANGA", "One Piece"))));

        assertEquals("Fourth Wing", service.search(Kind.BOOK, "wing", 10).get(0).title());
        assertEquals("One Piece", service.search(Kind.MANGA, "piece", 10).get(0).title());
    }

    @Test
    void returnsEmptyWhenNoProviderForKind() {
        CatalogService service = new CatalogService(List.of(
                new FakeProvider(Kind.BOOK, result("BOOK", "Fourth Wing"))));

        assertTrue(service.search(Kind.MANGA, "piece", 10).isEmpty());
    }
}
