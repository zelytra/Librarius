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
import zelytra.librarius.domain.Ownership;
import zelytra.librarius.library.OwnershipService;
import zelytra.librarius.web.ApiDtos.OwnershipDto;
import zelytra.librarius.web.ApiDtos.PriceDto;
import zelytra.librarius.web.ApiDtos.PagesDto;
import zelytra.librarius.web.ApiDtos.FormatDto;
import zelytra.librarius.web.ApiDtos.EditionChoiceDto;
import zelytra.librarius.domain.repository.LibraryItemRepository;
import zelytra.librarius.domain.repository.LibraryItemRepository.LibraryFilter;
import zelytra.librarius.domain.repository.LibraryItemRepository.LibrarySort;
import zelytra.librarius.domain.repository.RankCategoryRepository;
import zelytra.librarius.library.EditionService;
import zelytra.librarius.library.ReadingProgressService;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.EditionSwitchDto;
import zelytra.librarius.web.ApiDtos.LibraryCreateDto;
import zelytra.librarius.web.ApiDtos.LibraryItemDto;
import zelytra.librarius.web.ApiDtos.LibraryPageDto;
import zelytra.librarius.web.ApiDtos.ProgressDto;
import zelytra.librarius.web.ApiDtos.RankAssignDto;
import zelytra.librarius.web.ApiDtos.ReviewDto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The user's personal collection (owned books and mangas). */
@Path("/api/library")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class LibraryResource {

    /** Bounds of a personal rating, mirrored by {@code ReviewDto}. */
    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;

    @Inject
    CurrentUser currentUser;

    @Inject
    zelytra.librarius.domain.repository.ReadingProgressRepository progressRepository;

    @Inject
    LibraryItemRepository items;

    @Inject
    CatalogEntryService catalog;

    @Inject
    RankCategoryRepository categories;

    @Inject
    ReadingProgressService progress;

    @Inject
    EditionService editions;

    @Inject
    OwnershipService ownershipService;

    @Inject
    zelytra.librarius.series.SeriesVolumeRefresher seriesVolumes;

    /**
     * One page of the collection, filtered and sorted by the database.
     *
     * @param page      zero-based page index, clamped to 0 at the lowest
     * @param size      items per page, clamped to {@value PageRequest#MAX_SIZE}
     * @param sort      {@code added} (default), {@code title}, {@code author},
     *                  {@code genre} or {@code rating}
     * @param kind      {@code BOOK} or {@code MANGA}, no filtering when absent
     * @param status    {@code TO_READ}, {@code READING}, {@code READ} or {@code ABANDONED},
     *                  no filtering when absent — the four are exclusive, so an abandoned
     *                  title is returned by {@code status=ABANDONED} and by nothing else
     * @param rank      code of a rank category ({@code or}, {@code argent}, {@code bronze},
     *                  {@code abandon} or a custom one)
     * @param genre     code of a genre, as {@code /api/genres} returns it
     * @param minRating keeps the titles rated at least that much — 4 is "my favourites"
     * @param q         free text matched against the title, the authors and the series
     * @param standalone only the titles that belong to no series, the one-shots the grouped
     *                  shelf lists beside its series blocks
     * @param ownership {@code OWNED}, {@code BORROWED}, {@code EBOOK}, {@code WISHLIST} or
     *                  {@code NOT_OWNED} (#411), no filtering when absent
     * @param format    the format of the reader's copy (#447), no filtering when absent
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
            @QueryParam("minRating") Integer minRating,
            @QueryParam("q") String q,
            @QueryParam("standalone") @DefaultValue("false") boolean standalone,
            @QueryParam("ownership") Ownership ownership,
            @QueryParam("format") zelytra.librarius.domain.BookFormat format) {

        LibrarySort ordering = LibrarySort.parse(sort)
                .orElseThrow(() -> new BadRequestException("Unknown sort: " + sort));
        if (minRating != null && (minRating < MIN_RATING || minRating > MAX_RATING)) {
            throw new BadRequestException("minRating must be between 1 and 5");
        }
        PageRequest window = PageRequest.of(page, size);
        LibraryFilter filter =
                new LibraryFilter(currentUser.id(), kind, status, rank, genre, minRating, q,
                        standalone, ownership, format);

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
        item.status = dto.status() != null ? dto.status() : LibraryStatus.TO_READ;
        item.rating = dto.rating();
        item.acquiredAt = dto.acquiredAt();
        item.format = dto.format();
        items.persist(item);
        ownershipService.apply(item, dto.ownership());
        // A run this title opens learns its length once the add commits (#429).
        if (edition.work.series != null && edition.work.series.totalVolumes == null) {
            seriesVolumes.refreshAfterCommit(edition.work.series.id);
        }
        // Added straight as read: read now, as far as anyone can tell. Without a finished
        // reading the title counted in no year, no goal and no monthly statistic.
        if (item.status == LibraryStatus.READ) {
            zelytra.librarius.domain.ReadingProgress progress = new zelytra.librarius.domain.ReadingProgress();
            progress.libraryItem = item;
            progress.percent = 100;
            progress.currentPage = item.effectivePageCount();
            progress.finishedAt = java.time.LocalDate.now();
            progressRepository.persist(progress);
            item.progress = progress;
        }
        // Added as being read: started today (#392), so the reading has a start to count from.
        if (item.status == LibraryStatus.READING) {
            zelytra.librarius.domain.ReadingProgress progress = new zelytra.librarius.domain.ReadingProgress();
            progress.libraryItem = item;
            progress.startedAt = java.time.LocalDate.now();
            progressRepository.persist(progress);
            item.progress = progress;
        }

        return Response.status(Response.Status.CREATED).entity(LibraryItemDto.of(item)).build();
    }

    /**
     * Whether the reader has the book, and in what form (#411). {@code WISHLIST} puts the
     * edition on the wishlist as well; any other value takes it off.
     */
    @PUT
    @Path("/{id}/ownership")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response setOwnership(@PathParam("id") UUID id, @Valid OwnershipDto dto) {
        LibraryItem item = items.findOwned(currentUser.id(), id).orElse(null);
        if (item == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        ownershipService.apply(item, dto.ownership());
        return Response.ok(LibraryItemDto.of(item)).build();
    }

    /**
     * The reader's own price for their copy (#431). Private to this title: the shared edition's
     * market price is left as it is. A null price clears it.
     */
    @PUT
    @Path("/{id}/price")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response setPrice(@PathParam("id") UUID id, @Valid PriceDto dto) {
        LibraryItem item = items.findOwned(currentUser.id(), id).orElse(null);
        if (item == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        item.price = dto != null ? dto.price() : null;
        return Response.ok(LibraryItemDto.of(item)).build();
    }

    /**
     * The reader's own page count for their copy (#446), private to this title: every figure
     * of the reader uses it before the edition's. Null clears it.
     */
    @PUT
    @Path("/{id}/pages")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response setPages(@PathParam("id") UUID id, @Valid PagesDto dto) {
        LibraryItem item = items.findOwned(currentUser.id(), id).orElse(null);
        if (item == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        item.pageCount = dto != null ? dto.pageCount() : null;
        // The position follows the new length, as it does on a change of edition.
        if (item.progress != null && item.progress.percent != null) {
            item.progress.currentPage = zelytra.librarius.domain.ReadingProgress.pageOf(
                    item.progress.percent, item.effectivePageCount());
        }
        return Response.ok(LibraryItemDto.of(item)).build();
    }

    /** The format of the reader's copy (#447). Null clears it. */
    @PUT
    @Path("/{id}/format")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response setFormat(@PathParam("id") UUID id, FormatDto dto) {
        LibraryItem item = items.findOwned(currentUser.id(), id).orElse(null);
        if (item == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        item.format = dto != null ? dto.format() : null;
        return Response.ok(LibraryItemDto.of(item)).build();
    }

    /**
     * Moves the title onto an edition that is not a row yet (#445) — a provider's printing, or
     * one the reader describes — created under the title's own work.
     */
    @POST
    @Path("/{id}/edition")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response chooseEdition(@PathParam("id") UUID id, @Valid EditionChoiceDto dto) {
        EditionService.SwitchOutcome outcome = editions.chooseNewEdition(currentUser.id(), id, dto);
        if (outcome.ok()) {
            return Response.ok(LibraryItemDto.of(outcome.item())).build();
        }
        return switch (outcome.refusal()) {
            case UNKNOWN_ITEM -> Response.status(Response.Status.NOT_FOUND).build();
            case NOT_AN_EDITION_OF_THIS_WORK -> error(Response.Status.BAD_REQUEST,
                    "Cette édition n'appartient pas à la même œuvre.");
            case ALREADY_OWNED -> error(Response.Status.CONFLICT,
                    "Cette édition est déjà dans ta collection.");
        };
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

    /**
     * Updates the reading progress (and the status) of a title.
     *
     * <p>The transition rules and the page / percentage conversion belong to
     * {@link ReadingProgressService}; the resource only resolves the item, which is where
     * the ownership check lives.
     */
    @PUT
    @Path("/{id}/progress")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response setProgress(@PathParam("id") UUID id, @Valid ProgressDto dto) {
        LibraryItem item = items.findOwned(currentUser.id(), id).orElse(null);
        if (item == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        // An empty body clears the progress, the same way a null field does.
        progress.apply(item, dto != null ? dto : new ProgressDto(null, null, null, null, null));
        return Response.noContent().build();
    }

    /**
     * Records what the user thought of a title: a rating out of five and free-text notes.
     *
     * <p>Both are private. They sit on the caller's own item, so they are returned to
     * nobody else and are never folded into a shared score — an identifier belonging to
     * someone else answers 404, exactly like an unknown one.
     */
    @PUT
    @Path("/{id}/review")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response setReview(@PathParam("id") UUID id, @Valid ReviewDto dto) {
        LibraryItem item = items.findOwned(currentUser.id(), id).orElse(null);
        if (item == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        ReviewDto update = dto != null ? dto : new ReviewDto(null, null);
        item.rating = update.rating();
        item.review = blankToNull(update.review());
        // Absent leaves the reader's choice as it was: a client that predates sharing never
        // turns a shared review private, or the reverse.
        if (update.shared() != null) {
            item.reviewShared = update.shared();
        }
        return Response.ok(LibraryItemDto.of(item)).build();
    }

    /** An empty text area means "no review", not a row holding an empty string. */
    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    /**
     * Points the item at another edition of the same work — "this is the edition I own".
     *
     * <p>Only the materialisation changes: what the row records about the reader is left
     * alone, and the reading position follows the rules of {@link EditionService}. Owning
     * the same edition twice is what {@code UNIQUE(user_id, edition_id)} forbids, so a
     * switch onto an edition already in the collection is refused with a {@code 409} and a
     * message rather than with a constraint violation.
     */
    @PUT
    @Path("/{id}/edition")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response setEdition(@PathParam("id") UUID id, @Valid EditionSwitchDto dto) {
        EditionService.SwitchOutcome outcome =
                editions.switchEdition(currentUser.id(), id, dto != null ? dto.editionId() : null);
        if (outcome.ok()) {
            return Response.ok(LibraryItemDto.of(outcome.item())).build();
        }
        return switch (outcome.refusal()) {
            case UNKNOWN_ITEM -> Response.status(Response.Status.NOT_FOUND).build();
            case NOT_AN_EDITION_OF_THIS_WORK -> error(Response.Status.BAD_REQUEST,
                    "Cette édition n'appartient pas à la même œuvre.");
            case ALREADY_OWNED -> error(Response.Status.CONFLICT,
                    "Cette édition est déjà dans ta collection.");
        };
    }

    /**
     * A refusal that says why, in the shape {@code ImportExceptionMapper} already uses.
     * French like the import errors, the interface being French — though the PWA renders its
     * own copy from the status code, so this is what a second client, or a log, reads.
     */
    private static Response error(Response.Status status, String message) {
        return Response.status(status).entity(Map.of("message", message)).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") UUID id) {
        boolean removed = items.deleteOwned(currentUser.id(), id);
        return removed ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }
}
