package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
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
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.WishPriority;
import zelytra.librarius.domain.WishlistItem;
import zelytra.librarius.domain.repository.WishlistItemRepository;
import zelytra.librarius.domain.repository.WishlistItemRepository.WishlistFilter;
import zelytra.librarius.domain.repository.WishlistItemRepository.WishlistSort;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.WishlistCreateDto;
import zelytra.librarius.web.ApiDtos.WishlistItemDto;
import zelytra.librarius.web.ApiDtos.WishlistPageDto;

import java.util.List;
import java.util.UUID;

/** The user's wishlist. */
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

    /**
     * One page of the wishlist, filtered and sorted by the database.
     *
     * @param page     zero-based page index, clamped to 0 at the lowest
     * @param size     items per page, clamped to {@value PageRequest#MAX_SIZE}
     * @param sort     {@code priority} (default), {@code added}, {@code title},
     *                 {@code author} or {@code price}
     * @param kind     {@code BOOK} or {@code MANGA}, no filtering when absent
     * @param priority {@code PRIORITY}, {@code SOON} or {@code SOMEDAY}
     * @param q        free text matched against the title, the authors and the series
     */
    @GET
    public WishlistPageDto list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("" + PageRequest.DEFAULT_SIZE) int size,
            @QueryParam("sort") String sort,
            @QueryParam("kind") Kind kind,
            @QueryParam("priority") WishPriority priority,
            @QueryParam("q") String q) {

        WishlistSort ordering = WishlistSort.parse(sort)
                .orElseThrow(() -> new BadRequestException("Unknown sort: " + sort));
        PageRequest window = PageRequest.of(page, size);
        WishlistFilter filter = new WishlistFilter(currentUser.id(), kind, priority, q);

        long total = items.countMatching(filter);
        List<WishlistItemDto> slice =
                items.listMatching(filter, ordering, window.offset(), window.size())
                        .stream().map(WishlistItemDto::of).toList();
        return new WishlistPageDto(slice, window.page(), window.size(), total);
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
