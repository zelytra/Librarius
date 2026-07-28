package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import zelytra.librarius.library.EditionService;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.EditionDto;

import java.util.List;
import java.util.UUID;

/**
 * The works the user has a stake in, and the editions they exist in.
 *
 * <p>A work and its editions are shared catalog data, but this is not a catalog browser: a
 * work becomes visible to a user once they own an edition of it. Anything else answers 404
 * like an unknown identifier — a 403 would confirm that the work exists in someone else's
 * collection, which is what {@code SeriesResource} already refuses to do.
 */
@Path("/api/works")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class WorkResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    EditionService editions;

    /**
     * The known editions of a work: paperback, hardcover, original language, collector's.
     *
     * <p>Always includes the edition the caller owns, so a single request draws the whole
     * section; each entry says whether it is already in their collection.
     *
     * @throws NotFoundException when the work does not exist, or the caller owns nothing of
     *                          it
     */
    @GET
    @Path("/{id}/editions")
    public List<EditionDto> list(@PathParam("id") UUID id) {
        return editions.editionsOf(currentUser.id(), id).orElseThrow(NotFoundException::new);
    }
}
