package zelytra.librarius.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.GoalUnit;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.domain.ReadingGoal;
import zelytra.librarius.domain.Series;
import zelytra.librarius.domain.WishPriority;
import zelytra.librarius.domain.WishlistItem;
import zelytra.librarius.domain.Work;

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
            return new BookView(e.id, w.kind.name(), w.title, w.authors, w.seriesTitle,
                    w.volumeNumber, e.coverUrl, e.pageCount, e.publisher, e.language, e.isbn13,
                    w.originalYear, w.synopsis, w.genres);
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

    public record LibraryItemDto(UUID id, String status, Integer rating, LocalDate acquiredAt,
            String rankCode, BookView book) {
        public static LibraryItemDto of(LibraryItem it) {
            return new LibraryItemDto(it.id, it.status.name(), it.rating, it.acquiredAt,
                    it.rankCategory != null ? it.rankCategory.code : null, BookView.of(it.edition));
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

    /** One page of the wishlist. Same envelope as {@link LibraryPageDto}. */
    public record WishlistPageDto(java.util.List<WishlistItemDto> items, int page, int size,
            long total) {
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

    public record ProgressDto(Integer currentPage, Integer percent, LibraryStatus status) {
    }

    public record StatsDto(long read, long reading, long toRead, long pagesRead, long seriesCount,
            Integer goalTarget, long goalCurrent, java.util.List<GenreCount> byGenre) {
    }

    public record GenreCount(String genre, long count) {
    }

    public record WishlistCreateDto(
            @NotNull @Valid ManualBookDto book,
            WishPriority priority,
            BigDecimal estimatedPrice,
            String note) {
    }

    public record WishlistItemDto(UUID id, String priority, BigDecimal estimatedPrice, String note,
            BookView book) {
        public static WishlistItemDto of(WishlistItem it) {
            return new WishlistItemDto(it.id, it.priority.name(), it.estimatedPrice, it.note,
                    BookView.of(it.edition));
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
