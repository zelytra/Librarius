package zelytra.librarius.catalog.provider;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
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

    record SearchResponse(List<Doc> docs) {
    }

    record Doc(
            String title,
            @JsonProperty("author_name") List<String> authorName,
            @JsonProperty("first_publish_year") Integer firstPublishYear,
            @JsonProperty("cover_i") Long coverId,
            List<String> isbn,
            List<String> publisher,
            List<String> language) {
    }
}
