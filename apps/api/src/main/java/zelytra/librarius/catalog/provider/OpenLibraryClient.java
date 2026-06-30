package zelytra.librarius.catalog.provider;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

/** Client REST de l'API de recherche Open Library (livres, sans clé). */
@RegisterRestClient(configKey = "openlibrary")
public interface OpenLibraryClient {

    @GET
    @Path("/search.json")
    @Produces(MediaType.APPLICATION_JSON)
    SearchResponse search(@QueryParam("q") String q,
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
