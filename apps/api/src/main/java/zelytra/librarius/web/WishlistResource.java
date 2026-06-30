package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import zelytra.librarius.catalog.CatalogEntryService;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.WishPriority;
import zelytra.librarius.domain.WishlistItem;
import zelytra.librarius.domain.repository.WishlistItemRepository;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.WishlistCreateDto;
import zelytra.librarius.web.ApiDtos.WishlistItemDto;

import java.util.List;
import java.util.UUID;

/** Liste de souhaits de l'utilisateur. */
@Path("/api/wishlist")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class WishlistResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    WishlistItemRepository items;

    @Inject
    CatalogEntryService catalog;

    @GET
    public List<WishlistItemDto> list() {
        return items.listByUser(currentUser.id()).stream().map(WishlistItemDto::of).toList();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response add(@Valid WishlistCreateDto dto) {
        currentUser.require();
        Edition edition = catalog.createManualEdition(dto.book());

        WishlistItem item = new WishlistItem();
        item.userId = currentUser.id();
        item.edition = edition;
        item.priority = dto.priority() != null ? dto.priority() : WishPriority.SOON;
        item.estimatedPrice = dto.estimatedPrice();
        item.note = dto.note();
        items.persist(item);

        return Response.status(Response.Status.CREATED).entity(WishlistItemDto.of(item)).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") UUID id) {
        boolean removed = items.deleteOwned(currentUser.id(), id);
        return removed ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }
}
