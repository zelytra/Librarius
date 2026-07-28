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
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PUT;
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
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.domain.ReadingProgress;
import zelytra.librarius.domain.repository.LibraryItemRepository;
import zelytra.librarius.domain.repository.LibraryItemRepository.LibraryFilter;
import zelytra.librarius.domain.repository.LibraryItemRepository.LibrarySort;
import zelytra.librarius.domain.repository.RankCategoryRepository;
import zelytra.librarius.domain.repository.ReadingProgressRepository;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.LibraryCreateDto;
import zelytra.librarius.web.ApiDtos.LibraryItemDto;
import zelytra.librarius.web.ApiDtos.LibraryPageDto;
import zelytra.librarius.web.ApiDtos.ProgressDto;
import zelytra.librarius.web.ApiDtos.RankAssignDto;

import java.util.List;
import java.util.UUID;

/** The user's personal collection (owned books and mangas). */
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

    @Inject
    RankCategoryRepository categories;

    @Inject
    ReadingProgressRepository progresses;

    /**
     * One page of the collection, filtered and sorted by the database.
     *
     * @param page   zero-based page index, clamped to 0 at the lowest
     * @param size   items per page, clamped to {@value PageRequest#MAX_SIZE}
     * @param sort   {@code added} (default), {@code title}, {@code author} or {@code genre}
     * @param kind   {@code BOOK} or {@code MANGA}, no filtering when absent
     * @param status {@code OWNED}, {@code READING} or {@code READ}
     * @param rank   code of a rank category ({@code or}, {@code argent}, {@code bronze} or
     *               a custom one)
     * @param genre  code of a genre, as {@code /api/genres} returns it
     * @param q      free text matched against the title, the authors and the series
     */
    @GET
    public LibraryPageDto list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("" + PageRequest.DEFAULT_SIZE) int size,
            @QueryParam("sort") String sort,
            @QueryParam("kind") Kind kind,
            @QueryParam("status") LibraryStatus status,
            @QueryParam("rank") String rank,
            @QueryParam("genre") String genre,
            @QueryParam("q") String q) {

        LibrarySort ordering = LibrarySort.parse(sort)
                .orElseThrow(() -> new BadRequestException("Unknown sort: " + sort));
        PageRequest window = PageRequest.of(page, size);
        LibraryFilter filter = new LibraryFilter(currentUser.id(), kind, status, rank, genre, q);

        long total = items.countMatching(filter);
        List<LibraryItemDto> slice =
                items.listMatching(filter, ordering, window.offset(), window.size())
                        .stream().map(LibraryItemDto::of).toList();
        return new LibraryPageDto(slice, window.page(), window.size(), total);
    }

    /**
     * A single owned title. Lets a client deep-link to a detail screen without paging
     * through the whole collection to find the item.
     *
     * <p>An identifier belonging to someone else answers 404 like an unknown one: a 403
     * would confirm that the item exists.
     *
     * @throws NotFoundException when the item does not exist, or is not the caller's
     */
    @GET
    @Path("/{id}")
    public LibraryItemDto get(@PathParam("id") UUID id) {
        return items.findOwned(currentUser.id(), id)
                .map(LibraryItemDto::of)
                .orElseThrow(NotFoundException::new);
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

    /** Assigns a rank to an owned title (or clears it when categoryId is null). */
    @PUT
    @Path("/{id}/rank")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response setRank(@PathParam("id") UUID id, RankAssignDto dto) {
        LibraryItem item = items.findOwned(currentUser.id(), id).orElse(null);
        if (item == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (dto == null || dto.categoryId() == null) {
            item.rankCategory = null;
        } else {
            var cat = categories.findForUser(currentUser.id(), dto.categoryId()).orElse(null);
            if (cat == null) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
            item.rankCategory = cat;
        }
        return Response.ok(LibraryItemDto.of(item)).build();
    }

    /** Updates the reading progress (and the status) of a title. */
    @PUT
    @Path("/{id}/progress")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response setProgress(@PathParam("id") UUID id, ProgressDto dto) {
        LibraryItem item = items.findOwned(currentUser.id(), id).orElse(null);
        if (item == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (dto.status() != null) {
            item.status = dto.status();
        }
        ReadingProgress progress = progresses.findByItem(id).orElseGet(() -> {
            ReadingProgress p = new ReadingProgress();
            p.libraryItem = item;
            progresses.persist(p);
            return p;
        });
        progress.currentPage = dto.currentPage();
        progress.percent = dto.percent();
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") UUID id) {
        boolean removed = items.deleteOwned(currentUser.id(), id);
        return removed ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }
}
