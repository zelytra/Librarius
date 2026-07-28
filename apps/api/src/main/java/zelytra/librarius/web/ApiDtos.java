package zelytra.librarius.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.GoalUnit;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.domain.ReadingGoal;
import zelytra.librarius.domain.ReadingProgress;
import zelytra.librarius.domain.Series;
import zelytra.librarius.domain.WishPriority;
import zelytra.librarius.domain.WishlistItem;
import zelytra.librarius.domain.Work;
import zelytra.librarius.domain.repository.LibraryItemRepository.GenreTotal;
import zelytra.librarius.domain.repository.WishlistItemRepository.PriorityBudget;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** REST API transfer objects (DTOs), grouped together to stay compact. */
public final class ApiDtos {

    private ApiDtos() {
    }

    public record MeDto(String id, String email, String displayName, String locale) {
        public static MeDto of(AppUser u) {
            return new MeDto(u.id, u.email, u.displayName, u.locale);
        }
    }

    /** Denormalized "book" view (work + edition) returned to the front end. */
    public record BookView(
            UUID editionId,
            String kind,
            String title,
            String authors,
            String seriesTitle,
            Integer volumeNumber,
            String coverUrl,
            Integer pageCount,
            String publisher,
            String language,
            String isbn13,
            Integer originalYear,
            String synopsis,
            String genres) {
        public static BookView of(Edition e) {
            Work w = e.work;
            // genresText, not the normalised genres: reading the association here would
            // cost one query per item of a page of the collection.
            return new BookView(e.id, w.kind.name(), w.title, w.authors, w.seriesTitle,
                    w.volumeNumber, e.coverUrl, e.pageCount, e.publisher, e.language, e.isbn13,
                    w.originalYear, w.synopsis, w.genresText);
        }
    }

    /** Manual entry of a book (before the external catalog is integrated). */
    public record ManualBookDto(
            @NotNull Kind kind,
            @NotBlank String title,
            String authors,
            String seriesTitle,
            Integer volumeNumber,
            String isbn13,
            String publisher,
            String language,
            Integer pageCount,
            String coverUrl,
            String format,
            LocalDate releaseDate,
            Integer originalYear,
            String synopsis,
            String genres) {
    }

    public record LibraryCreateDto(
            @NotNull @Valid ManualBookDto book,
            LibraryStatus status,
            Integer rating,
            LocalDate acquiredAt) {
    }

    /**
     * Where the reader stands in a title, as the screens display it.
     *
     * <p>Both sides of the position are always filled when the edition carries a page
     * count, whichever one the user typed: a client renders a bar from {@code percent} and
     * a "page x of y" line from {@code currentPage} without having to convert anything, and
     * two screens can never disagree on the same book.
     *
     * @param currentPage page the reader stopped on, {@code null} when unknown
     * @param percent     share of the book read, 0 to 100
     */
    public record ProgressView(Integer currentPage, Integer percent, LocalDate startedAt,
            LocalDate finishedAt) {
        public static ProgressView of(ReadingProgress p, Integer pageCount) {
            Integer page = p.currentPage != null ? p.currentPage
                    : ReadingProgress.pageOf(p.percent, pageCount);
            Integer percent = p.percent != null ? p.percent
                    : ReadingProgress.percentOf(p.currentPage, pageCount);
            return new ProgressView(page, percent, p.startedAt, p.finishedAt);
        }
    }

    /**
     * An owned title.
     *
     * @param progress reading position, {@code null} while the title has never been opened
     */
    public record LibraryItemDto(UUID id, String status, Integer rating, LocalDate acquiredAt,
            String rankCode, ProgressView progress, BookView book) {
        public static LibraryItemDto of(LibraryItem it) {
            return new LibraryItemDto(it.id, it.status.name(), it.rating, it.acquiredAt,
                    it.rankCategory != null ? it.rankCategory.code : null,
                    it.progress != null ? ProgressView.of(it.progress, it.edition.pageCount) : null,
                    BookView.of(it.edition));
        }
    }

    /**
     * One page of the collection.
     *
     * <p>Envelope rather than a bare array: without {@code total} the client cannot tell
     * whether there is more to fetch, nor display a count without downloading everything —
     * which is exactly what the pagination is there to avoid. {@code page} and {@code size}
     * are echoed back because the server clamps them.
     *
     * @param items the slice itself, at most {@code size} entries
     * @param page  zero-based index of the returned page
     * @param size  effective page size, after clamping
     * @param total number of items matching the filter, all pages taken together
     */
    public record LibraryPageDto(java.util.List<LibraryItemDto> items, int page, int size,
            long total) {
    }

    /**
     * One page of the wishlist. Same envelope as {@link LibraryPageDto}, plus the budget.
     *
     * @param budget estimated spend of the whole filtered wishlist — identical on every
     *               page, exactly like {@code total}
     */
    public record WishlistPageDto(java.util.List<WishlistItemDto> items, int page, int size,
            long total, WishlistBudgetDto budget) {
    }

    public record CategoryDto(UUID id, String code, String label, String color, boolean builtin) {
        public static CategoryDto of(zelytra.librarius.domain.RankCategory c) {
            return new CategoryDto(c.id, c.code, c.label, c.color, c.builtin);
        }
    }

    public record CategoryCreateDto(@NotBlank String label, String color) {
    }

    public record RankAssignDto(java.util.UUID categoryId) {
    }

    /**
     * Where the reader now stands, and optionally the status that goes with it.
     *
     * <p>A PUT, not a patch: the payload describes the whole progress, so a null field
     * clears it. A client that only flips the status therefore hands the position back
     * untouched — which is what the detail screen does.
     *
     * <p>Only one of {@code currentPage} and {@code percent} needs filling in: the server
     * derives the other from the page count of the edition, so the two can never drift
     * apart. Sending both keeps them as they are.
     */
    public record ProgressDto(@PositiveOrZero Integer currentPage,
            @Min(0) @Max(100) Integer percent,
            LibraryStatus status,
            LocalDate startedAt,
            LocalDate finishedAt) {
    }

    public record StatsDto(long read, long reading, long toRead, long pagesRead, long seriesCount,
            Integer goalTarget, long goalCurrent, java.util.List<GenreCount> byGenre) {
    }

    /**
     * A genre and how many of the caller's titles carry it.
     *
     * @param code  identity of the genre, and what {@code /api/library?genre=} takes
     * @param genre the label to show — the field keeps its name so that the deployed front
     *              end, which reads it, goes on working
     */
    public record GenreCount(String code, String genre, long count) {
        public static GenreCount of(GenreTotal total) {
            return new GenreCount(total.code(), total.label(), total.count());
        }
    }

    /**
     * A new wish. The bounds on the price and the note are those of the columns: without
     * them an oversized value reaches PostgreSQL and comes back as a 500 rather than a 400.
     */
    public record WishlistCreateDto(
            @NotNull @Valid ManualBookDto book,
            WishPriority priority,
            @PositiveOrZero @Digits(integer = 6, fraction = 2) BigDecimal estimatedPrice,
            @Size(max = 512) String note) {
    }

    /**
     * What the user can change about a wish once it exists.
     *
     * <p>A PUT, not a patch: the three fields are replaced as a whole, so a null price or a
     * null note clears it. The priority is required — a wish always sits in one of the
     * buckets, and the column has no room for the absence of one.
     */
    public record WishlistUpdateDto(
            @NotNull WishPriority priority,
            @PositiveOrZero @Digits(integer = 6, fraction = 2) BigDecimal estimatedPrice,
            @Size(max = 512) String note) {
    }

    /**
     * What the user knows about the purchase when a wish becomes a book they own. Every
     * field is optional, body included: the title itself comes from the wish.
     */
    public record WishlistAcquireDto(LibraryStatus status, Integer rating, LocalDate acquiredAt) {
    }

    public record WishlistItemDto(UUID id, String priority, BigDecimal estimatedPrice, String note,
            BookView book) {
        public static WishlistItemDto of(WishlistItem it) {
            return new WishlistItemDto(it.id, it.priority.name(), it.estimatedPrice, it.note,
                    BookView.of(it.edition));
        }
    }

    /**
     * Estimated spend of one priority group of the wishlist.
     *
     * @param count       wishes in the group, whether they carry an estimate or not
     * @param pricedCount those of them that do
     * @param total       sum of those estimates
     */
    public record WishlistBudgetLineDto(String priority, long count, long pricedCount,
            BigDecimal total) {
        public static WishlistBudgetLineDto of(PriorityBudget b) {
            return new WishlistBudgetLineDto(b.priority().name(), b.count(), b.pricedCount(),
                    b.total());
        }
    }

    /**
     * What the wishlist would cost, for the wishes matching the current filters.
     *
     * <p>Rides on the list rather than living behind an endpoint of its own so that the
     * figure a client shows can never contradict the rows it shows underneath: one request,
     * one set of criteria, one answer.
     *
     * @param total       sum of every estimate, zero when no wish carries one
     * @param pricedCount wishes carrying an estimate — tells an empty wishlist apart from
     *                    one where nobody has entered a price yet
     * @param byPriority  the same figures per priority, most urgent first; priorities no
     *                    wish carries are absent rather than reported as zero
     */
    public record WishlistBudgetDto(BigDecimal total, long pricedCount,
            java.util.List<WishlistBudgetLineDto> byPriority) {
        public static WishlistBudgetDto of(java.util.List<PriorityBudget> groups) {
            BigDecimal total = groups.stream()
                    .map(PriorityBudget::total)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long priced = groups.stream().mapToLong(PriorityBudget::pricedCount).sum();
            return new WishlistBudgetDto(total, priced,
                    groups.stream().map(WishlistBudgetLineDto::of).toList());
        }
    }

    public record GoalDto(UUID id, int year, int targetCount, String unit) {
        public static GoalDto of(ReadingGoal g) {
            return new GoalDto(g.id, g.year, g.targetCount, g.unit.name());
        }
    }

    public record GoalUpsertDto(@NotNull @Min(1) Integer targetCount, GoalUnit unit) {
    }

    // ── Series ────────────────────────────────────────────────────────────────

    /**
     * A series as it appears in the user's list: the catalog data plus where the user
     * stands in the run.
     *
     * @param totalVolumes size of the complete run, {@code null} when the catalog does not
     *                     know it — an ongoing series usually does not
     * @param ownedCount   distinct volumes of the series in the user's collection
     * @param readCount    of those, the ones marked {@code READ}
     * @param followed     whether the user follows the series; private to each user
     */
    public record SeriesSummaryDto(UUID id, String kind, String title, String coverUrl,
            Integer totalVolumes, String status, long ownedCount, long readCount,
            boolean followed) {
        public static SeriesSummaryDto of(Series s, long ownedCount, long readCount,
                boolean followed) {
            return new SeriesSummaryDto(s.id, s.kind.name(), s.title, s.coverUrl, s.totalVolumes,
                    s.status != null ? s.status.name() : null, ownedCount, readCount, followed);
        }
    }

    /**
     * One volume of a series, seen through the user's collection.
     *
     * <p>The four flags are not exclusive: a read volume is also owned. {@code missing} and
     * {@code upcoming} both mean "not owned", on either side of the highest volume the user
     * owns — a hole in the run versus what is still ahead of them.
     *
     * @param title         title carried by the catalog, {@code null} for a volume nobody
     *                      has entered yet
     * @param libraryItemId the user's item for this volume, {@code null} when not owned
     */
    public record SeriesVolumeDto(Integer volumeNumber, String title, UUID workId,
            UUID libraryItemId, boolean owned, boolean read, boolean missing,
            boolean upcoming) {
    }

    /** A series, its counters and the state of each of its volumes. */
    public record SeriesDetailDto(UUID id, String kind, String title, String originalTitle,
            String coverUrl, String synopsis, Integer totalVolumes, String status,
            long ownedCount, long readCount, boolean followed,
            java.util.List<SeriesVolumeDto> volumes) {
        public static SeriesDetailDto of(Series s, long ownedCount, long readCount,
                boolean followed, java.util.List<SeriesVolumeDto> volumes) {
            return new SeriesDetailDto(s.id, s.kind.name(), s.title, s.originalTitle, s.coverUrl,
                    s.synopsis, s.totalVolumes, s.status != null ? s.status.name() : null,
                    ownedCount, readCount, followed, volumes);
        }
    }

    /**
     * The holes in an owned run: the volumes between the first and the highest one owned
     * that are not in the collection.
     */
    public record SeriesMissingDto(UUID seriesId, String title,
            java.util.List<Integer> volumes) {
    }
}
