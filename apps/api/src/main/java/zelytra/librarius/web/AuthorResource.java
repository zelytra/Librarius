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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import zelytra.librarius.author.AuthorService;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.AuthorDetailDto;
import zelytra.librarius.web.ApiDtos.AuthorSummaryDto;

import java.util.List;
import java.util.UUID;

/**
 * The authors of the shared catalog.
 *
 * <p>Unlike {@link SeriesResource}, this is a catalog browser and not a view of what the
 * caller owns: an author is meant to be found, so {@code /api/authors} and
 * {@code /api/authors/{id}} answer over the whole shared catalog, the same to every caller
 * but for the private {@code followed} flag. An unknown identifier is a 404; a known one
 * never is, whatever the caller collects. This is a different feature from
 * {@code GET /api/catalog/search?author=}, which queries the external providers for new
 * titles — this one searches authors Librarius already knows.
 */
@Path("/api/authors")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class AuthorResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    AuthorService authors;

    /**
     * Authors whose name matches {@code q}, with the caller's own {@code followed} flag. A
     * blank or absent term returns nothing rather than the whole catalog.
     */
    @GET
    public List<AuthorSummaryDto> search(@QueryParam("q") String q) {
        return authors.search(currentUser.id(), q);
    }

    /**
     * An author, their bibliography, and whether the caller follows them.
     *
     * @throws NotFoundException when no author carries this identifier
     */
    @GET
    @Path("/{id}")
    public AuthorDetailDto get(@PathParam("id") UUID id) {
        return authors.detail(currentUser.id(), id).orElseThrow(NotFoundException::new);
    }

    /** Starts following the author. Idempotent. */
    @PUT
    @Path("/{id}/follow")
    public Response follow(@PathParam("id") UUID id) {
        currentUser.require();
        return authors.follow(currentUser.id(), id)
                ? Response.noContent().build()
                : Response.status(Response.Status.NOT_FOUND).build();
    }

    /** Stops following the author. Idempotent. */
    @DELETE
    @Path("/{id}/follow")
    public Response unfollow(@PathParam("id") UUID id) {
        return authors.unfollow(currentUser.id(), id)
                ? Response.noContent().build()
                : Response.status(Response.Status.NOT_FOUND).build();
    }
}
