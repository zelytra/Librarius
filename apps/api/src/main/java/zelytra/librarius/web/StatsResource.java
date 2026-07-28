package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.stats.StatsService;
import zelytra.librarius.web.ApiDtos.StatsDto;

/**
 * The user's aggregated reading statistics.
 *
 * <p>Everything is aggregated by the database — see {@link StatsService}. The endpoint is
 * hit on every Home and Stats render, so its cost must not follow the number of titles in
 * the collection.
 */
@Path("/api/stats")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class StatsResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    StatsService stats;

    @GET
    public StatsDto stats() {
        return stats.stats(currentUser.id());
    }
}
