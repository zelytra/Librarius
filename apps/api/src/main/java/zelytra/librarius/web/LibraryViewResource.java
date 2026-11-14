package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import zelytra.librarius.library.LibraryViewService;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.LibraryViewDto;
import zelytra.librarius.web.ApiDtos.LibraryViewInput;
import zelytra.librarius.web.ApiDtos.LibraryViewOrderDto;

import java.util.List;
import java.util.UUID;

/**
 * The reader's saved library views (#451): how they lay their library out, and the order they
 * dragged its entries into. Another reader's view answers 404, like an unknown one.
 */
@Path("/api/views")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class LibraryViewResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    LibraryViewService views;

    @GET
    public List<LibraryViewDto> list() {
        currentUser.require();
        return views.list(currentUser.id());
    }

    /** A new view; 409 once the reader keeps {@value LibraryViewService#MAX_VIEWS}. */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @org.jboss.resteasy.reactive.ResponseStatus(201)
    public LibraryViewDto create(@Valid LibraryViewInput input) {
        currentUser.require();
        return views.create(currentUser.id(), input)
                .orElseThrow(() -> new jakarta.ws.rs.ClientErrorException(Response.Status.CONFLICT));
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public LibraryViewDto update(@PathParam("id") UUID id, @Valid LibraryViewInput input) {
        currentUser.require();
        return views.update(currentUser.id(), id, input).orElseThrow(jakarta.ws.rs.NotFoundException::new);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") UUID id) {
        currentUser.require();
        return views.delete(currentUser.id(), id)
                ? Response.noContent().build()
                : Response.status(Response.Status.NOT_FOUND).build();
    }

    /** Replaces the view's manual order with the keys given, first first. */
    @PUT
    @Path("/{id}/order")
    @Consumes(MediaType.APPLICATION_JSON)
    public LibraryViewDto reorder(@PathParam("id") UUID id, @Valid LibraryViewOrderDto order) {
        currentUser.require();
        return views.reorder(currentUser.id(), id, order.keys()).orElseThrow(jakarta.ws.rs.NotFoundException::new);
    }
}
