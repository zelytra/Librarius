package zelytra.librarius.web;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.time.Instant;
import java.util.Map;

/**
 * Minimal smoke endpoint, kept to check that the Quarkus skeleton starts and
 * answers. Will be replaced by the business resources in the following PRs.
 */
@Path("/api/hello")
public class HelloResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> hello() {
        return Map.of(
                "app", "Librarius API",
                "message", "Bonjour 👋",
                "timestamp", Instant.now().toString());
    }
}
