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

/** Client REST de l'API GraphQL AniList (mangas). */
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

    record Data(@JsonProperty("Page") Page page) {
    }

    record Page(List<Media> media) {
    }

    record Media(int id, Title title, FuzzyDate startDate, Cover coverImage, String description,
            Staff staff) {
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
