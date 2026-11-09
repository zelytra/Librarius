package zelytra.librarius.catalog.provider;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import zelytra.librarius.catalog.CatalogQuery;
import zelytra.librarius.catalog.CatalogResult;

import java.time.Duration;
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
        public Uni<GqlResponse> query(GqlRequest body) {
            request = body;
            return Uni.createFrom().item(
                    new GqlResponse(new Data(null, new StaffSearch(new MediaConnection(staffMedia)))));
        }
    }

    private static AniListClient.Media media(String title, Integer year, Boolean adult) {
        return new AniListClient.Media(1, new AniListClient.Title(title, null),
                new AniListClient.FuzzyDate(year, null, null), null, null, adult, null, null);
    }

    private final CapturingClient client = new CapturingClient();

    private AniListProvider provider() {
        return provider(client, Duration.ofSeconds(5));
    }

    private AniListProvider provider(AniListClient client, Duration timeout) {
        AniListProvider provider = new AniListProvider();
        provider.client = client;
        provider.callTimeout = timeout;
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

    @Test
    void givesUpOnAProviderThatNeverAnswers() {
        // `read-timeout` bounds silence, not slowness — Vert.x restarts that timer on every
        // chunk. The deadline below is the only thing that stops a cold fetch from holding a
        // database connection for as long as AniList feels like taking.
        AniListProvider provider =
                provider(body -> Uni.createFrom().nothing(), Duration.ofMillis(200));

        long start = System.nanoTime();
        List<CatalogResult> results = provider.search(CatalogQuery.of("never answers"), 20);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(results.isEmpty(), "a timed-out call degrades into an empty result");
        assertTrue(elapsedMs < 5_000, "the call must give up on its own, took " + elapsedMs + "ms");
    }

    private static AniListClient.Staff staff(String... roleAndName) {
        List<AniListClient.StaffEdge> edges = new java.util.ArrayList<>();
        for (int i = 0; i < roleAndName.length; i += 2) {
            edges.add(new AniListClient.StaffEdge(roleAndName[i],
                    new AniListClient.StaffNode(new AniListClient.Name(roleAndName[i + 1]))));
        }
        return new AniListClient.Staff(edges);
    }

    @Test
    void creditsTheAuthorRatherThanTheFirstAssistantListed() {
        // Live AniList answer for Attack on Titan: an assistant comes first in the staff list.
        assertEquals("Hajime Isayama", AniListProvider.creators(staff(
                "Assistant", "Tatsuya Endou",
                "Story & Art", "Hajime Isayama",
                "Assistant", "Yuusuke Nomura")));
    }

    @Test
    void creditsTheWriterAndTheArtistOfASplitTeam() {
        assertEquals("Kanehito Yamada, Tsukasa Abe", AniListProvider.creators(staff(
                "Translator (French)", "Géraldine Oudin",
                "Art", "Tsukasa Abe",
                "Story", "Kanehito Yamada")));
    }

    @Test
    void prefersWhoeverDrewASpinOffOverTheOriginalCreator() {
        assertEquals("Ei Andou", AniListProvider.creators(staff(
                "Original Creator", "Eiichirou Oda",
                "Story & Art (ch 1-7)", "Ei Andou",
                "Translator (German)", "Antje Bockel")));
    }

    @Test
    void creditsNobodyWhenNoCreativeRoleIsListed() {
        assertEquals(null, AniListProvider.creators(staff("Assistant", "Someone", "Editing", "Else")));
        assertEquals(null, AniListProvider.creators(null));
    }

    @Test
    void searchesTheSeriesOfAVolumeSearch() {
        provider().search(CatalogQuery.of("one piece tome 3"), 20);
        assertEquals("one piece", client.request.variables().get("q"));

        provider().search(CatalogQuery.of("Kaiju No. 8"), 20);
        assertEquals("Kaiju No. 8", client.request.variables().get("q"));
    }

    @Test
    void asksForAJapaneseNameInAniListsSpellingWhenTheTypedOneIsUnknown() {
        assertEquals(List.of("Eiichiro Oda", "Eiichirou Oda"), AniListProvider.staffSpellings("Eiichiro Oda"));
        assertEquals(List.of("Eiichirō Oda", "Eiichirou Oda"), AniListProvider.staffSpellings("Eiichirō Oda"));
        assertEquals(List.of("Hajime Isayama"), AniListProvider.staffSpellings("Hajime Isayama"));
    }
}
