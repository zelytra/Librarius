package zelytra.librarius.catalog.provider;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

/**
 * REST client for the Open Library search API (books, no API key needed).
 *
 * <p>Returns a {@code Uni} rather than the response itself so the caller can put an absolute
 * deadline on the call. {@code read-timeout} does not provide one: Vert.x restarts that timer
 * on every chunk received, so it bounds silence, not slowness.
 */
@RegisterRestClient(configKey = "openlibrary")
public interface OpenLibraryClient {

    @GET
    @Path("/search.json")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<SearchResponse> search(@QueryParam("q") String q,
            @QueryParam("limit") int limit,
            @QueryParam("fields") String fields);

    /**
     * The editions of a work, the source for "the other printings of this title". {@code ref}
     * is the Open Library work key with no {@code /works/} prefix (e.g. {@code OL45804W}), the
     * shape {@code work.provider_ref} carries.
     */
    @GET
    @Path("/works/{ref}/editions.json")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<EditionsResponse> editions(@PathParam("ref") String ref, @QueryParam("limit") int limit);

    record SearchResponse(List<Doc> docs) {
    }

    record Doc(
            String title,
            @JsonProperty("author_name") List<String> authorName,
            @JsonProperty("first_publish_year") Integer firstPublishYear,
            @JsonProperty("cover_i") Long coverId,
            List<String> isbn,
            List<String> publisher,
            List<String> language,
            @JsonProperty("number_of_pages_median") Integer numberOfPagesMedian) {
    }

    record EditionsResponse(List<EditionEntry> entries) {
    }

    /**
     * One edition record of a work. Open Library carries the publisher and the ISBN as lists,
     * the language as a list of {@code /languages/<marc>} keys and the covers as a list of
     * numeric ids; the provider takes the first usable value of each.
     */
    record EditionEntry(
            String key,
            @JsonProperty("isbn_13") List<String> isbn13,
            @JsonProperty("isbn_10") List<String> isbn10,
            List<String> publishers,
            List<LanguageRef> languages,
            List<Long> covers,
            @JsonProperty("physical_format") String physicalFormat) {
    }

    record LanguageRef(String key) {
    }
}
