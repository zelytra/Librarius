package zelytra.librarius.web;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.time.Instant;
import java.util.Map;

/**
 * Endpoint de fumée minimal, présent pour vérifier que le squelette Quarkus
 * démarre et répond. Sera remplacé par les ressources métier dans les PR suivantes.
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
