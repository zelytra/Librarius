package zelytra.librarius.catalog.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import zelytra.librarius.catalog.CatalogProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * The real providers behind stubbed REST clients that answer what each catalogue really answered
 * for a query, recorded under {@code catalog/openlibrary} and {@code catalog/bnf}. Lets a test
 * outside this package (the relevance suite) run the providers' own mapping without the network.
 */
public final class RecordedProviders {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private RecordedProviders() {
    }

    /** Open Library answering the recorded {@code openlibrary/<slug>.json} to every search. */
    public static CatalogProvider openLibrary(String slug) {
        OpenLibraryProvider provider = new OpenLibraryProvider();
        provider.client = new OpenLibraryClient() {
            @Override
            public Uni<SearchResponse> search(String q, int limit, String fields, String sort, String lang) {
                try (InputStream in = resource("/catalog/openlibrary/" + slug + ".json")) {
                    return Uni.createFrom().item(MAPPER.readValue(in, SearchResponse.class));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public Uni<EditionsResponse> editions(String ref, int limit) {
                return Uni.createFrom().item(new EditionsResponse(List.of()));
            }
        };
        provider.callTimeout = TIMEOUT;
        return provider;
    }

    /** The BnF answering the recorded phrase fixture to the {@code adj} query, the all-words one otherwise. */
    public static CatalogProvider bnf(String slug) {
        BnfProvider provider = new BnfProvider();
        provider.client = (version, operation, query, schema, max) -> Uni.createFrom().item(
                text("/catalog/bnf/" + slug + (query.contains(" adj ") ? ".phrase" : ".all") + ".xml"));
        provider.callTimeout = TIMEOUT;
        return provider;
    }

    /**
     * AniList answering, for a title search, the series given for the query asked ({@code q},
     * matched case-insensitively), and nothing for any other query.
     *
     * @param series query → {english title, romaji title, author} of the one series it answers
     */
    public static CatalogProvider aniList(Map<String, String[]> series) {
        AniListProvider provider = new AniListProvider();
        provider.client = body -> {
            Object q = body.variables() == null ? null : body.variables().get("q");
            String[] hit = q == null ? null : series.get(q.toString().toLowerCase(java.util.Locale.ROOT));
            List<AniListClient.Media> media = hit == null ? List.of() : List.of(new AniListClient.Media(
                    Math.abs(hit[0].hashCode()), new AniListClient.Title(hit[1], hit[0]),
                    new AniListClient.FuzzyDate(2018, 8, 1),
                    new AniListClient.Cover("https://s4.anilist.co/file/anilistcdn/media/manga/cover/large/x.jpg"),
                    "A manga.", false, null,
                    new AniListClient.Staff(List.of(new AniListClient.StaffEdge("Story & Art",
                            new AniListClient.StaffNode(new AniListClient.Name(hit[2])))))));
            return Uni.createFrom().item(new AniListClient.GqlResponse(
                    new AniListClient.Data(new AniListClient.Page(media), null)));
        };
        provider.callTimeout = TIMEOUT;
        return provider;
    }

    private static InputStream resource(String path) {
        InputStream in = RecordedProviders.class.getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("missing fixture " + path);
        }
        return in;
    }

    private static String text(String path) {
        try (InputStream in = resource(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
