package zelytra.librarius.catalog.provider;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

/** Client REST de l'API Google Books (livres). */
@RegisterRestClient(configKey = "googlebooks")
public interface GoogleBooksClient {

    @GET
    @Path("/volumes")
    @Produces(MediaType.APPLICATION_JSON)
    Volumes search(@QueryParam("q") String q,
            @QueryParam("maxResults") int maxResults,
            @QueryParam("langRestrict") String langRestrict,
            @QueryParam("key") String key);

    record Volumes(List<Item> items) {
    }

    record Item(String id, VolumeInfo volumeInfo) {
    }

    record VolumeInfo(String title, List<String> authors, String publishedDate, String publisher,
            String language, Integer pageCount, String description, ImageLinks imageLinks,
            List<IndustryId> industryIdentifiers) {
    }

    record ImageLinks(String thumbnail) {
    }

    record IndustryId(String type, String identifier) {
    }
}
