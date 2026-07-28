package zelytra.librarius.catalog.provider;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;
import java.util.Map;

/** REST client for the AniList GraphQL API (mangas). */
@RegisterRestClient(configKey = "anilist")
@Path("/")
public interface AniListClient {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    GqlResponse query(GqlRequest body);

    record GqlRequest(String query, Map<String, Object> variables) {
    }

    record GqlResponse(Data data) {
    }

    /**
     * Root of the answer. Only one of the two branches is ever filled, depending on the query
     * that was sent: {@code Page} for a title search, {@code Staff} for an author search.
     */
    record Data(@JsonProperty("Page") Page page, @JsonProperty("Staff") StaffSearch staff) {
    }

    record Page(List<Media> media) {
    }

    /** A person, and the works AniList credits them with. */
    record StaffSearch(MediaConnection staffMedia) {
    }

    record MediaConnection(List<Media> nodes) {
    }

    record Media(int id, Title title, FuzzyDate startDate, Cover coverImage, String description,
            Boolean isAdult, Staff staff) {
    }

    record Title(String romaji, String english) {
    }

    record FuzzyDate(Integer year, Integer month, Integer day) {
    }

    record Cover(String large) {
    }

    record Staff(List<StaffEdge> edges) {
    }

    record StaffEdge(String role, StaffNode node) {
    }

    record StaffNode(Name name) {
    }

    record Name(String full) {
    }
}
