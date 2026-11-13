package zelytra.librarius.web;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import zelytra.librarius.covers.CoverService;

import java.util.Optional;

/**
 * Resized, cached thumbnails of the covers the BnF holds.
 *
 * <p>The one anonymous endpoint of the API ({@code @PermitAll}, the others being
 * {@code @Authenticated}): an {@code <img>} cannot send a bearer token, and a catalogue cover
 * is not user data. Nothing is scoped by user here because nothing user-owned is read.
 * Hidden from the OpenAPI schema: it is a binary endpoint no generated client calls.
 */
@Path("/api/covers/bnf")
@PermitAll
public class CoverResource {

    @Inject
    CoverService covers;

    @GET
    @Path("/{ark}")
    @Operation(hidden = true)
    public Response bnf(@PathParam("ark") String ark) {
        Optional<byte[]> cover = CoverService.isValid(ark) ? covers.bnfCover(ark) : Optional.empty();
        if (cover.isPresent()) {
            return Response.ok(cover.get(), "image/jpeg")
                    .header("Cache-Control", "public, max-age=2592000, immutable")
                    .build();
        }
        return Response.status(Response.Status.NOT_FOUND)
                .header("Cache-Control", "public, max-age=86400")
                .build();
    }
}
