package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.series.SeriesService;
import zelytra.librarius.web.ApiDtos.SeriesDetailDto;
import zelytra.librarius.web.ApiDtos.SeriesMissingDto;
import zelytra.librarius.web.ApiDtos.SeriesSummaryDto;

import java.util.List;
import java.util.UUID;

/**
 * The series the user has a stake in.
 *
 * <p>A series is shared catalog data, but it becomes visible to a user only once they own a
 * volume of it or follow it. An identifier outside that set answers 404 like an unknown
 * one: a 403 would confirm that the series exists in someone else's collection.
 */
@Path("/api/series")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class SeriesResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    SeriesService series;

    /** Series owned or followed by the user, with where they stand in each run. */
    @GET
    public List<SeriesSummaryDto> list() {
        return series.listForUser(currentUser.id());
    }

    /**
     * A series and the state of each of its volumes.
     *
     * @throws NotFoundException when the series does not exist, or is not the caller's
     */
    @GET
    @Path("/{id}")
    public SeriesDetailDto get(@PathParam("id") UUID id) {
        return series.detail(currentUser.id(), id).orElseThrow(NotFoundException::new);
    }

    /**
     * Volumes missing from the user's run: those below the highest volume they own that are
     * not in their collection. Owning 1, 2 and 5 reports 3 and 4.
     */
    @GET
    @Path("/{id}/missing")
    public SeriesMissingDto missing(@PathParam("id") UUID id) {
        return series.missing(currentUser.id(), id).orElseThrow(NotFoundException::new);
    }

    /** Starts following the series. Idempotent. */
    @PUT
    @Path("/{id}/follow")
    public Response follow(@PathParam("id") UUID id) {
        currentUser.require();
        return series.follow(currentUser.id(), id)
                ? Response.noContent().build()
                : Response.status(Response.Status.NOT_FOUND).build();
    }

    /** Stops following the series. Idempotent. */
    @DELETE
    @Path("/{id}/follow")
    public Response unfollow(@PathParam("id") UUID id) {
        return series.unfollow(currentUser.id(), id)
                ? Response.noContent().build()
                : Response.status(Response.Status.NOT_FOUND).build();
    }
}
