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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import zelytra.librarius.catalog.CatalogEntryService;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.domain.repository.LibraryItemRepository;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.LibraryCreateDto;
import zelytra.librarius.web.ApiDtos.LibraryItemDto;

import java.util.List;
import java.util.UUID;

/** Collection personnelle de l'utilisateur (livres et mangas possédés). */
@Path("/api/library")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class LibraryResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    LibraryItemRepository items;

    @Inject
    CatalogEntryService catalog;

    @GET
    public List<LibraryItemDto> list(@QueryParam("status") LibraryStatus status) {
        String userId = currentUser.id();
        List<LibraryItem> found = status != null
                ? items.listByUserAndStatus(userId, status)
                : items.listByUser(userId);
        return found.stream().map(LibraryItemDto::of).toList();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response add(@Valid LibraryCreateDto dto) {
        currentUser.require();
        Edition edition = catalog.createManualEdition(dto.book());

        LibraryItem item = new LibraryItem();
        item.userId = currentUser.id();
        item.edition = edition;
        item.status = dto.status() != null ? dto.status() : LibraryStatus.OWNED;
        item.rating = dto.rating();
        item.acquiredAt = dto.acquiredAt();
        items.persist(item);

        return Response.status(Response.Status.CREATED).entity(LibraryItemDto.of(item)).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") UUID id) {
        boolean removed = items.deleteOwned(currentUser.id(), id);
        return removed ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }
}
