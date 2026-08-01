package zelytra.librarius.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.domain.Author;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.GoalUnit;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.domain.ReadingGoal;
import zelytra.librarius.domain.ReadingProgress;
import zelytra.librarius.domain.ReportReason;
import zelytra.librarius.domain.ReportStatus;
import zelytra.librarius.domain.ReportTargetType;
import zelytra.librarius.domain.Series;
import zelytra.librarius.domain.WishPriority;
import zelytra.librarius.domain.WishlistItem;
import zelytra.librarius.domain.Work;
import zelytra.librarius.domain.repository.LibraryItemRepository.GenreTotal;
import zelytra.librarius.domain.repository.ReadingProgressRepository.LabelTotal;
import zelytra.librarius.domain.repository.ReadingProgressRepository.PeriodTotal;
import zelytra.librarius.domain.repository.WishlistItemRepository.PriorityBudget;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** REST API transfer objects (DTOs), grouped together to stay compact. */
public final class ApiDtos {

    private ApiDtos() {
    }

    /**
     * @param trusted the server-computed trust flag (V16, #180), read straight off
     *                {@link AppUser#trusted} — never client input, and there is no field on
     *                {@link UpdateMeDto} that can set it. Surfaced here so the Settings screen
     *                can show the badge next to the caller's own name (#186)
     */
    public record MeDto(String id, String email, String displayName, String locale,
                        String timeZone, boolean trusted) {
        public static MeDto of(AppUser u) {
            return new MeDto(u.id, u.email, u.displayName, u.locale, u.timeZone, u.trusted);
        }
    }

    /**
     * Another member as it appears in a follow list (#200): the identifier a follow is issued
     * against and the display name a screen shows. It deliberately carries no email nor any
     * other personal field — a follow list exposes who, not how to reach them.
     *
     * @param trusted same server-computed flag as {@link MeDto#trusted()} (#180), so a follow
     *                list can show the trusted badge next to a member's name exactly as the
     *                caller's own profile does (#186)
     */
    public record MemberSummaryDto(String id, String displayName, boolean trusted) {
        public static MemberSummaryDto of(AppUser u) {
            return new MemberSummaryDto(u.id, u.displayName, u.trusted);
        }
    }

    /**
     * Editable profile fields (#75). A full replacement of the three the user owns, not a
     * sparse patch: the profile form always sends every one, so a field left out is a mistake
     * rather than "leave it as it was".
     *
     * <p>{@code locale} is one of the two the interface ships. {@code timeZone} is optional —
     * blank clears it, back to the device's zone — and when present must parse as a
     * {@link java.time.ZoneId}, which the resource checks: Bean Validation cannot, and a bad
     * identifier is a 400 like any other malformed input.
     */
    public record UpdateMeDto(
            @NotBlank @Size(max = 255) String displayName,
            @NotBlank @Pattern(regexp = "fr|en") String locale,
            @Size(max = 64) String timeZone) {
    }

    /**
     * Denormalized "book" view (work + edition) returned to the front end.
     *
     * @param editionId the materialisation the user owns — publisher, ISBN, page count
     * @param workId    the work behind it, which several editions may share. What
     *                  {@code /api/works/{id}/editions} takes, so a screen holding a book
     *                  can ask for the other editions of the same title
     */
    public record BookView(
            UUID editionId,
            UUID workId,
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
            return new BookView(e.id, w.id, w.kind.name(), w.title, w.authorsText, w.seriesTitle,
                    w.volumeNumber, e.coverUrl, e.pageCount, e.publisher, e.language, e.isbn13,
                    w.originalYear, w.synopsis, w.genresText);
        }
    }

    /**
     * One edition of a work, as the "other editions" section of the detail screen shows it.
     *
     * <p>Catalog data, and shared as such: it says what exists, never who owns it. The one
     * user-scoped field is {@code owned}, and it describes the caller's own collection.
     *
     * @param owned whether the caller already has this very edition — a switch onto it is
     *              what {@code UNIQUE(user_id, edition_id)} forbids, so the section marks it
     *              rather than offering an action the server would refuse
     */
    public record EditionDto(UUID id, String isbn13, String publisher, String language,
            Integer pageCount, String format, LocalDate releaseDate, String coverUrl,
            boolean owned) {
        public static EditionDto of(Edition e, boolean owned) {
            return new EditionDto(e.id, e.isbn13, e.publisher, e.language, e.pageCount, e.format,
                    e.releaseDate, e.coverUrl, owned);
        }
    }

    /**
     * Which edition of the work the user actually owns.
     *
     * <p>Moves the ownership row onto another materialisation of the same work; everything
     * the row carries about the reader — status, rating, review, rank, dates — describes the
     * reader and not the object, and is left untouched. See
     * {@link zelytra.librarius.library.EditionService} for what happens to the reading
     * position, the one field a change of edition does not leave alone.
     */
    public record EditionSwitchDto(@NotNull UUID editionId) {
    }

    /**
     * A book as the client describes it: everything the server needs to match or create the
     * work and the edition behind it, and nothing that identifies a row of this instance.
     *
     * <p>The name is a leftover. It is what a hand-typed entry sends, but also what a
     * Discover result is converted into and what an export carries — one shape for the three
     * paths, so the server never has two ways to record a title.
     *
     * @param provider    catalog the entry was picked from, {@code null} for a hand-typed one
     * @param providerRef identifier of the record in that catalog. Meaningful only next to
     *                    {@code provider}, and stored only when both are present
     */
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
            String genres,
            @Size(max = 32) String provider,
            @Size(max = 255) String providerRef) {
    }

    public record LibraryCreateDto(
            @NotNull @Valid ManualBookDto book,
            LibraryStatus status,
            @Min(1) @Max(5) Integer rating,
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
     * @param rating   personal rating, 1 to 5 — private to the owner
     * @param review   private notes on the title, never shared nor aggregated
     * @param progress reading position, {@code null} while the title has never been opened
     */
    public record LibraryItemDto(UUID id, String status, Integer rating, String review,
            LocalDate acquiredAt, String rankCode, ProgressView progress, BookView book) {
        public static LibraryItemDto of(LibraryItem it) {
            return new LibraryItemDto(it.id, it.status.name(), it.rating, it.review,
                    it.acquiredAt, it.rankCategory != null ? it.rankCategory.code : null,
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

    /**
     * Renaming a category. The label is all there is to change: the code follows from it,
     * and the colour is not editable — no screen offers a picker, so a field nothing can
     * fill would only be a way of clearing what the creation set.
     */
    public record CategoryUpdateDto(@NotBlank String label) {
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

    /**
     * What the reader thought of a title. Strictly private: it is stored on the user's own
     * {@code library_item}, is returned to nobody else, and is never aggregated into a
     * shared score.
     *
     * <p>A PUT of the pair, so clearing the rating is sending a null one — the alternative,
     * a partial update, gives no way to say "I no longer want to rate this".
     */
    public record ReviewDto(@Min(1) @Max(5) Integer rating, @Size(max = 5000) String review) {
    }

    /**
     * The counters the Home and Stats screens are built on.
     *
     * @param abandoned   titles given up on. A counter of its own rather than a share of one
     *                    of the three above: an abandoned title is neither read nor on the
     *                    to-read pile, and a client summing the counters to tell an empty
     *                    collection from a full one needs it in the sum
     * @param pagesRead   pages of the titles read to the end, the abandoned ones excluded
     * @param goalTarget  target of the current year's goal, {@code null} when none is set
     * @param goalUnit    unit that target is expressed in, {@code null} alongside it
     * @param goalCurrent how far the user is into that target, counted in {@code goalUnit}
     *                    over the titles finished this year — see {@link TimelineDto}
     */
    public record StatsDto(long read, long reading, long toRead, long abandoned, long pagesRead,
            long seriesCount, Integer goalTarget, String goalUnit, long goalCurrent,
            java.util.List<GenreCount> byGenre) {
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
     * What the user finished during one bucket of the timeline.
     *
     * @param period {@code 2026-03} at month granularity, {@code 2026} at year granularity
     */
    public record TimelinePointDto(String period, long books, long pages) {
        public static TimelinePointDto of(PeriodTotal total) {
            String period = total.month() == null
                    ? String.valueOf(total.year())
                    : "%04d-%02d".formatted(total.year(), total.month());
            return new TimelinePointDto(period, total.books(), total.pages());
        }
    }

    /** One line of a breakdown: a label and the titles finished carrying it. */
    public record BreakdownCountDto(String label, long count) {
        public static BreakdownCountDto of(LabelTotal total) {
            return new BreakdownCountDto(total.label(), total.count());
        }
    }

    /**
     * Reading over time, built on the day each title was finished.
     *
     * <p>Only the buckets the user read something in are listed: a month with no reading is
     * an absent point rather than a zero, so the payload follows the data and not the range
     * asked for. A client charting a full year pads the gaps itself.
     *
     * @param books        titles finished over the whole window
     * @param pages        their pages, editions with no page count contributing nothing
     * @param pagesPerDay  reading pace over the elapsed part of the window
     * @param daysPerBook  average number of days between starting and finishing a title,
     *                     {@code null} when no title in the window carries both dates
     * @param bestPeriod   the bucket with the most titles, {@code null} on an empty window
     * @param byAuthor     the most read authors, most read first; same for the three others
     */
    public record TimelineDto(LocalDate from, LocalDate to, String granularity,
            java.util.List<TimelinePointDto> points, long books, long pages, double pagesPerDay,
            Double daysPerBook, String bestPeriod, long bestPeriodBooks,
            java.util.List<BreakdownCountDto> byAuthor,
            java.util.List<BreakdownCountDto> byPublisher,
            java.util.List<BreakdownCountDto> byLanguage,
            java.util.List<BreakdownCountDto> byRank) {
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

    // ── Authors ─────────────────────────────────────────────────────────────

    /**
     * An author as the name search lists them: the shared catalog row, how many works of the
     * shared catalog credit them, and the caller's own follow flag.
     *
     * <p>Unlike a series summary, this carries no ownership counter: {@code /api/authors} is a
     * catalog browser open to anyone, so what it says about an author is the same for every
     * caller but for {@code followed}, which is private to each user.
     *
     * @param photoUrl portrait, {@code null} until a provider supplies one
     * @param workCount works of the shared catalog crediting the author, whoever owns them
     * @param followed  whether the caller follows the author; private to each user
     */
    public record AuthorSummaryDto(UUID id, String name, String photoUrl, long workCount,
            boolean followed) {
        public static AuthorSummaryDto of(Author a, long workCount, boolean followed) {
            return new AuthorSummaryDto(a.id, a.name, a.photoUrl, workCount, followed);
        }
    }

    /**
     * One work of an author's bibliography: enough of {@link BookView}'s shape to link into
     * the title, drawn from the shared catalog rather than from any one collection.
     *
     * <p>The cover is that of a representative edition of the work — the catalog holds covers
     * on editions, not on works — so a work nobody has entered a cover for carries a
     * {@code null} one. {@code authors} is the whole credit line, so the bibliography can show
     * an author's co-writers on each title.
     *
     * @param coverUrl cover of a representative edition, {@code null} when none carries one
     */
    public record AuthorWorkDto(UUID workId, String kind, String title, String authors,
            String seriesTitle, Integer volumeNumber, Integer originalYear, String coverUrl) {
        public static AuthorWorkDto of(Work w, String coverUrl) {
            return new AuthorWorkDto(w.id, w.kind.name(), w.title, w.authorsText, w.seriesTitle,
                    w.volumeNumber, w.originalYear, coverUrl);
        }
    }

    /**
     * An author opened by identifier: the shared catalog row, the full bibliography reachable
     * through {@code work_author}, and the caller's own follow flag.
     *
     * <p>The whole catalog is visible — an author is meant to be found, not recognised only
     * once owned — so the only user-scoped field is {@code followed}.
     */
    public record AuthorDetailDto(UUID id, String name, String photoUrl, boolean followed,
            java.util.List<AuthorWorkDto> works) {
        public static AuthorDetailDto of(Author a, boolean followed,
                java.util.List<AuthorWorkDto> works) {
            return new AuthorDetailDto(a.id, a.name, a.photoUrl, followed, works);
        }
    }

    // ── Export & account deletion (GDPR) ──────────────────────────────────────

    /**
     * A whole account, as {@code GET /api/export?format=json} hands it back and as
     * {@code POST /api/import/json} takes it in.
     *
     * <p>Nothing here carries a database identifier: the document describes what the user
     * entered, not the rows it happens to be stored in. That is what makes it re-importable
     * into a fresh account — and readable by another tool, which has no use for our UUIDs.
     *
     * <p>Every list is ordered deterministically (see {@code ExportService}), so exporting,
     * re-importing and exporting again produces the same document but for
     * {@code exportedAt}. The round trip is therefore checked by comparing two documents
     * rather than by walking the schema field by field.
     *
     * @param schemaVersion shape of this document, bumped when a field changes meaning
     * @param exportedAt    when the document was produced, the only value that varies
     *                      between two exports of an unchanged account
     */
    public record ExportDto(
            int schemaVersion,
            java.time.OffsetDateTime exportedAt,
            ExportUserDto user,
            java.util.List<ExportCategoryDto> categories,
            java.util.List<ExportGoalDto> goals,
            java.util.List<ExportCollectionItemDto> collection,
            java.util.List<ExportWishDto> wishlist,
            java.util.List<ExportSeriesFollowDto> followedSeries) {
    }

    /** The profile itself. The identifier is the Keycloak subject, no credential exists. */
    public record ExportUserDto(String id, String email, String displayName, String locale) {
        public static ExportUserDto of(AppUser u) {
            return new ExportUserDto(u.id, u.email, u.displayName, u.locale);
        }
    }

    /**
     * A ranking category the user created. The built-ins are shared rows carrying no
     * {@code user_id}, so they are not the user's data and are not exported; a
     * {@code rankCode} pointing at one still resolves on import, for every account has them.
     */
    public record ExportCategoryDto(String code, String label, String color, int sortOrder) {
    }

    public record ExportGoalDto(int year, int targetCount, GoalUnit unit) {
    }

    /**
     * Where the user stands in a title they own.
     *
     * <p>The stored values, not the ones {@link ProgressView} derives: an archive has to
     * describe what is in the row, so that restoring it puts the same row back. Deriving
     * here would write a page number the user never entered.
     */
    public record ExportProgressDto(Integer currentPage, Integer percent, LocalDate startedAt,
            LocalDate finishedAt) {
    }

    /**
     * One owned title: the book as the user would enter it, plus everything they added on
     * top of it.
     *
     * @param rating   personal rating, 1 to 5
     * @param review   the private notes on the title — the most personal thing the account
     *                 holds, and the first thing a portability export would be wrong to drop
     * @param rankCode code of the rank category the title is filed under, {@code null} when
     *                 it carries no rank
     */
    public record ExportCollectionItemDto(@NotNull @Valid ManualBookDto book,
            LibraryStatus status, Integer rating, String review, LocalDate acquiredAt,
            String rankCode, ExportProgressDto progress) {
    }

    /** One wish, with the note the user attached to it. */
    public record ExportWishDto(@NotNull @Valid ManualBookDto book, WishPriority priority,
            BigDecimal estimatedPrice, String note) {
    }

    /**
     * A series the user follows, named rather than referenced: {@code (kind, title)} is the
     * unique key of the shared {@code series} row, so an import resolves it — or creates it
     * — exactly like adding a volume does.
     */
    public record ExportSeriesFollowDto(Kind kind, String title) {
    }

    /**
     * A deferred export. Handed back with a {@code 202} when the account is too large to
     * serialise inside the request, and polled through {@code GET /api/export/{id}}.
     *
     * @param id     identifier to poll; it is the caller's, and answers 404 to anyone else
     * @param status {@code PENDING}, {@code READY} or {@code FAILED}
     * @param rows   titles the export covers — collection plus wishlist
     */
    public record ExportJobDto(UUID id, String status, String format, int rows,
            java.time.OffsetDateTime createdAt) {
    }

    /**
     * What deleting the account actually erased.
     *
     * <p>Returned rather than a bare 204 so the confirmation screen can state what has gone,
     * and so an integration test reads the counts from the API instead of trusting it.
     */
    public record AccountDeletionDto(int libraryItems, int wishlistItems, int goals,
            int categories, int seriesFollows) {
    }

    // ── Upcoming releases ─────────────────────────────────────────────────────

    /**
     * One announced release of a series the caller has a stake in.
     *
     * <p>Every field describing the date travels with it, because a release date on its own
     * is not a fact a screen can display: {@code region} says which edition it belongs to,
     * {@code datePrecision} how much of it is real, {@code confidence} whether anybody
     * committed to it, and {@code source} where it comes from. A client that shows the date
     * without them shows a precision the data does not have.
     *
     * @param releaseDate   first day of the announced window, {@code null} when the volume
     *                      is known to be coming and not known to be dated
     * @param datePrecision {@code DAY}, {@code MONTH}, {@code QUARTER} or {@code YEAR} —
     *                      {@code null} exactly when {@code releaseDate} is
     * @param region        {@code FR}, {@code JP} or {@code EN}: the market the date applies
     *                      to, never to be dropped when rendering
     * @param source        {@code manual}, {@code catalog}, or the provider it came from
     */
    public record UpcomingReleaseDto(UUID id, UUID seriesId, String seriesTitle, String kind,
            String coverUrl, Integer volumeNumber, String title, LocalDate releaseDate,
            String datePrecision, String region, String publisher, String source,
            String confidence) {
        public static UpcomingReleaseDto of(zelytra.librarius.domain.UpcomingRelease r) {
            Series s = r.series;
            return new UpcomingReleaseDto(r.id, s.id, s.title, s.kind.name(), s.coverUrl,
                    r.volumeNumber, r.title, r.releaseDate,
                    r.datePrecision != null ? r.datePrecision.name() : null,
                    r.region.name(), r.publisher, r.source, r.confidence.name());
        }
    }

    // ── Dashboard layout ──────────────────────────────────────────────────────

    /**
     * One section of the Home dashboard: its code and whether the user hid it.
     *
     * <p>{@code code} is deliberately a plain string rather than a closed enum: the set of
     * sections is meant to grow, and a layout saved before a new one existed must go on
     * working rather than fail to deserialise. An unrecognised code is dropped rather than
     * rejected — see {@link zelytra.librarius.dashboard.DashboardLayoutService}.
     */
    public record DashboardSectionDto(@NotBlank String code, boolean hidden) {
    }

    /**
     * The Home screen's layout: which sections show, and in which order.
     *
     * <p>Always complete on the way out: {@code GET} fills in whatever the user never
     * touched — every section, the first time — so the client never has to know the
     * defaults itself. A {@code PUT} is a full replace, the same convention as
     * {@link ProgressDto}: a section left out of the body is not "leave it as it is", the
     * next {@code GET} adds it back in, visible.
     */
    public record DashboardLayoutDto(@NotNull @Valid java.util.List<DashboardSectionDto> sections) {
    }

    /**
     * A member's report that a shared catalog object carries an error (#192).
     *
     * <p>Write-only input: the caller names what to flag ({@code targetType} + {@code targetId}),
     * why ({@code reason}, a closed picklist), and optionally the specifics ({@code comment}).
     * The reporter is never in the body — it is always {@code CurrentUser.id()}. Reporting an
     * unknown {@code targetId} is a 400, checked by {@code ReportService} since no single
     * foreign key can span the three target tables.
     */
    public record ReportCreateDto(
            @NotNull ReportTargetType targetType,
            @NotNull UUID targetId,
            @NotNull ReportReason reason,
            @Size(max = 2000) String comment) {
    }

    /**
     * The report just created, echoed back to its author on the {@code POST}.
     *
     * <p>This is not a way to read a report back — there is no {@code GET} — it is the created
     * resource returned to the one person who filed it. It carries nothing about anybody else:
     * {@code reporterId} is the caller and therefore left off.
     */
    public record ReportDto(UUID id, ReportTargetType targetType, UUID targetId,
            ReportReason reason, String comment, ReportStatus status,
            java.time.OffsetDateTime createdAt) {
        public static ReportDto of(zelytra.librarius.domain.Report r) {
            return new ReportDto(r.id, r.targetType, r.targetId, r.reason, r.comment, r.status,
                    r.createdAt);
        }
    }
}
