package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import zelytra.librarius.contribution.CatalogEditService;
import zelytra.librarius.domain.CatalogEditTarget;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.CatalogEditCreateDto;
import zelytra.librarius.web.ApiDtos.CatalogEditDto;
import zelytra.librarius.web.ApiDtos.CatalogEditFieldsDto;

import java.util.UUID;

/**
 * A member proposing a change to a shared catalog record (#618). The record is catalog data,
 * readable by every member; what is scoped to the caller is the proposal, whose author is always
 * {@code CurrentUser.id()}. Reviewing proposals is {@code /api/admin/catalog-edits}.
 */
@Path("/api/catalog-edits")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class CatalogEditResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    CatalogEditService edits;

    /** The form's starting point: the record's current values. 404 for an unknown record. */
    @GET
    @Path("/fields")
    public CatalogEditFieldsDto fields(@QueryParam("targetType") @NotNull CatalogEditTarget targetType,
            @QueryParam("targetId") @NotNull UUID targetId) {
        currentUser.require();
        return edits.current(currentUser.id(), targetType, targetId);
    }

    /**
     * Submits a proposal. 201 with where it stands: {@code APPLIED} straight away for an admin, or
     * when a store page it links to confirmed the book; {@code PENDING} otherwise. 400 on a bad or
     * empty proposal, 404 on an unknown record, 409 when one of the caller's is already pending.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @org.eclipse.microprofile.openapi.annotations.responses.APIResponse(responseCode = "201",
            content = @org.eclipse.microprofile.openapi.annotations.media.Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @org.eclipse.microprofile.openapi.annotations.media.Schema(implementation = CatalogEditDto.class)))
    public Response submit(@Valid @NotNull CatalogEditCreateDto dto) {
        currentUser.require();
        CatalogEditDto edit = edits.submit(currentUser.id(), currentUser.isAdmin(), dto);
        return Response.status(Response.Status.CREATED).entity(edit).build();
    }
}
