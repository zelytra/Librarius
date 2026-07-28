package zelytra.librarius.catalog.provider;

import org.junit.jupiter.api.Test;
import zelytra.librarius.catalog.CatalogQuery;
import zelytra.librarius.catalog.CatalogResult;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks which criteria reach AniList and which it cannot honour, without any network. */
class AniListProviderTest {

    /** Captures the GraphQL request, and answers with a canned staff page. */
    private static final class CapturingClient implements AniListClient {

        private GqlRequest request;

        private List<AniListClient.Media> staffMedia = List.of();

        @Override
        public GqlResponse query(GqlRequest body) {
            request = body;
            return new GqlResponse(new Data(null, new StaffSearch(new MediaConnection(staffMedia))));
        }
    }

    private static AniListClient.Media media(String title, Integer year, Boolean adult) {
        return new AniListClient.Media(1, new AniListClient.Title(title, null),
                new AniListClient.FuzzyDate(year, null, null), null, null, adult, null);
    }

    private final CapturingClient client = new CapturingClient();

    private AniListProvider provider() {
        AniListProvider provider = new AniListProvider();
        provider.client = client;
        return provider;
    }

    @Test
    void searchesTheTitleAndTheYearThroughTheMediaQuery() {
        provider().search(new CatalogQuery("naruto", null, 1999, null, null, null), 20);

        String gql = client.request.query();
        assertTrue(gql.contains("search: $q"), gql);
        assertTrue(gql.contains("startDate_greater: $from"), gql);
        Map<String, Object> variables = client.request.variables();
        assertEquals("naruto", variables.get("q"));
        assertEquals(19990101, variables.get("from"));
        assertEquals(19991231, variables.get("to"));
    }

    @Test
    void leavesTheDateWindowOutWhenNoYearIsAsked() {
        // AniList rejects a null startDate_greater outright, so the window is only declared
        // when a year is actually being filtered on.
        provider().search(CatalogQuery.of("naruto"), 20);

        assertFalse(client.request.query().contains("startDate_greater"), client.request.query());
    }

    @Test
    void resolvesAnAuthorThroughTheStaffQuery() {
        // AniList links a work to the staff who made it: there is no author field to search.
        client.staffMedia = List.of(media("NARUTO", 1999, false));

        List<CatalogResult> results =
                provider().search(new CatalogQuery(null, "Masashi Kishimoto", null, null, null, null), 20);

        assertTrue(client.request.query().contains("Staff(search: $a)"), client.request.query());
        assertEquals("Masashi Kishimoto", client.request.variables().get("a"));
        assertEquals(List.of("NARUTO"), results.stream().map(CatalogResult::title).toList());
    }

    @Test
    void narrowsTheAuthorsWorksOnTheTitleAndTheYear() {
        client.staffMedia = List.of(media("NARUTO", 1999, false), media("BORUTO", 2016, false));

        List<CatalogResult> results = provider()
                .search(new CatalogQuery("boruto", "Masashi Kishimoto", null, null, null, null), 20);

        assertEquals(List.of("BORUTO"), results.stream().map(CatalogResult::title).toList());

        results = provider()
                .search(new CatalogQuery(null, "Masashi Kishimoto", 1999, null, null, null), 20);

        assertEquals(List.of("NARUTO"), results.stream().map(CatalogResult::title).toList());
    }

    @Test
    void keepsExplicitWorksOutOfAnAuthorSearch() {
        // The title query filters them server-side; a staff's works carry no such argument.
        client.staffMedia = List.of(media("NARUTO", 1999, false), media("Explicit", 2001, true));

        List<CatalogResult> results =
                provider().search(new CatalogQuery(null, "Masashi Kishimoto", null, null, null, null), 20);

        assertEquals(List.of("NARUTO"), results.stream().map(CatalogResult::title).toList());
    }

    @Test
    void answersNothingWhenOnlyBookCriteriaAreGiven() {
        // AniList describes works, not editions: it holds no publisher, language or ISBN.
        // Ignoring them and returning the most popular mangas would look like a result.
        assertTrue(provider().search(
                new CatalogQuery(null, null, null, "fr", "Glénat", "9782344012345"), 20).isEmpty());
    }
}
