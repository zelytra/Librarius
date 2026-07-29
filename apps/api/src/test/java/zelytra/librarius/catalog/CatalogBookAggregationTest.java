package zelytra.librarius.catalog;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import zelytra.librarius.catalog.provider.BnfClient;
import zelytra.librarius.catalog.provider.OpenLibraryClient;
import zelytra.librarius.domain.Kind;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * The two book providers behind the real {@link CatalogService} and the real two-level cache,
 * with only the outbound HTTP replaced.
 *
 * <p>What is being checked is the aggregate, not either provider: that both sources reach the
 * caller, that a book both of them know is shown once, and that one of them being down costs
 * its own results and nothing else. Each test searches for something of its own — the cache
 * is application-scoped and would otherwise serve one test's answer to another.
 */
@QuarkusTest
class CatalogBookAggregationTest {

    @InjectMock
    @RestClient
    OpenLibraryClient openLibrary;

    @InjectMock
    @RestClient
    BnfClient bnf;

    @Inject
    CatalogService catalog;

    private static String sru(String title, String creator) {
        return """
                <srw:searchRetrieveResponse xmlns:srw="http://www.loc.gov/zing/srw/">
                  <srw:records><srw:record><srw:recordData>
                    <dc xmlns="http://purl.org/dc/elements/1.1/">
                      <title>%s</title>
                      <creator>%s</creator>
                      <publisher>Pocket</publisher>
                      <date>DL 2001</date>
                      <language>fre</language>
                      <identifier>http://catalogue.bnf.fr/ark:/12148/cb37652488r</identifier>
                    </dc>
                  </srw:recordData></srw:record></srw:records>
                </srw:searchRetrieveResponse>
                """.formatted(title, creator);
    }

    private void openLibraryAnswers(String title, String author) {
        OpenLibraryClient.Doc doc = new OpenLibraryClient.Doc(title, List.of(author), 2023, 42L,
                List.of("9781234567890"), List.of("Piatkus"), List.of("eng"));
        Mockito.when(openLibrary.search(anyString(), anyInt(), anyString()))
                .thenReturn(Uni.createFrom().item(new OpenLibraryClient.SearchResponse(List.of(doc))));
    }

    private void bnfAnswers(String xml) {
        Mockito.when(bnf.search(anyString(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(Uni.createFrom().item(xml));
    }

    private void bnfIsDown() {
        Mockito.when(bnf.search(anyString(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(Uni.createFrom().failure(new IllegalStateException("connect timed out")));
    }

    private List<String> titlesFor(String text) {
        return catalog.search(Kind.BOOK, CatalogQuery.of(text), 10).stream()
                .map(CatalogResult::title)
                .toList();
    }

    @Test
    void aBookSearchMergesBothProviders() {
        openLibraryAnswers("Fourth Wing", "Rebecca Yarros");
        bnfAnswers(sru("La Horde du Contrevent", "Damasio, Alain (1969-....). Auteur du texte"));

        List<CatalogResult> results = catalog.search(Kind.BOOK, CatalogQuery.of("merge both"), 10);

        assertEquals(List.of("bnf", "openlibrary"),
                results.stream().map(CatalogResult::provider).sorted().toList());
    }

    @Test
    void aTitleOnlyOneProviderKnowsStillSurfaces() {
        // Open Library's French holdings are thin; a title only the BnF has must not be lost
        // in the merge, and the reverse must hold too.
        openLibraryAnswers("Fourth Wing", "Rebecca Yarros");
        bnfAnswers(sru("La Horde du Contrevent", "Damasio, Alain (1969-....). Auteur du texte"));

        List<String> titles = titlesFor("only one knows");

        assertTrue(titles.contains("Fourth Wing"), titles.toString());
        assertTrue(titles.contains("La Horde du Contrevent"), titles.toString());
    }

    @Test
    void aBookBothProvidersKnowIsListedOnce() {
        // The BnF writes an authority heading where Open Library writes a plain name. The
        // provider puts it back in reading order precisely so that the aggregation's
        // title+author key matches and the novel is not shown twice.
        openLibraryAnswers("Dune", "Frank Herbert");
        bnfAnswers(sru("Dune", "Herbert, Frank (1920-1986). Auteur du texte"));

        assertEquals(List.of("Dune"), titlesFor("listed once"));
    }

    @Test
    void oneProviderBeingDownCostsOnlyItsOwnResults() {
        openLibraryAnswers("Fourth Wing", "Rebecca Yarros");
        bnfIsDown();

        // Not an error, and not an empty answer either: the search degrades to the provider
        // that is still up.
        assertEquals(List.of("Fourth Wing"), titlesFor("one provider down"));
    }

    @Test
    void bothProvidersBeingDownAnswerNothingRatherThanFailing() {
        Mockito.when(openLibrary.search(anyString(), anyInt(), anyString()))
                .thenReturn(Uni.createFrom().failure(new IllegalStateException("connect timed out")));
        bnfIsDown();

        assertTrue(titlesFor("both providers down").isEmpty());
    }
}
