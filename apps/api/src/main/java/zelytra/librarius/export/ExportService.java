package zelytra.librarius.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.ReadingProgress;
import zelytra.librarius.domain.Work;
import zelytra.librarius.domain.repository.AppUserRepository;
import zelytra.librarius.domain.repository.LibraryItemRepository;
import zelytra.librarius.domain.repository.RankCategoryRepository;
import zelytra.librarius.domain.repository.ReadingGoalRepository;
import zelytra.librarius.domain.repository.SeriesFollowRepository;
import zelytra.librarius.domain.repository.SeriesRepository;
import zelytra.librarius.domain.repository.WishlistItemRepository;
import zelytra.librarius.web.ApiDtos.ExportCategoryDto;
import zelytra.librarius.web.ApiDtos.ExportCollectionItemDto;
import zelytra.librarius.web.ApiDtos.ExportDto;
import zelytra.librarius.web.ApiDtos.ExportGoalDto;
import zelytra.librarius.web.ApiDtos.ExportProgressDto;
import zelytra.librarius.web.ApiDtos.ExportSeriesFollowDto;
import zelytra.librarius.web.ApiDtos.ExportUserDto;
import zelytra.librarius.web.ApiDtos.ExportWishDto;
import zelytra.librarius.web.ApiDtos.ManualBookDto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Builds the two representations of an account: the complete JSON archive, and the flat CSV
 * book list.
 *
 * <p>Every read is scoped to the {@code userId} it is handed. Nothing here resolves the
 * caller by itself — the service is also driven from the background pool of
 * {@link ExportJobs}, where there is no request to derive an identity from, so the scope has
 * to be an argument rather than an ambient value.
 */
@ApplicationScoped
public class ExportService {

    /**
     * Shape of the JSON document. Bumped when a field changes meaning, so that an import can
     * refuse a document it would misread rather than silently mangle it.
     */
    public static final int SCHEMA_VERSION = 1;

    @Inject
    AppUserRepository users;

    @Inject
    LibraryItemRepository items;

    @Inject
    WishlistItemRepository wishes;

    @Inject
    ReadingGoalRepository goals;

    @Inject
    RankCategoryRepository categories;

    @Inject
    SeriesFollowRepository follows;

    @Inject
    SeriesRepository series;

    @Inject
    ObjectMapper json;

    /** An export, ready to be written to the wire. */
    public record ExportFile(String filename, ExportFormat format, byte[] content) {
    }

    /**
     * Titles the export would cover — the collection plus the wishlist.
     *
     * <p>Counted rather than measured: it is what decides between serialising inside the
     * request and handing back a job, and asking the database for two counts is far cheaper
     * than building the document to find out how big it is.
     */
    @Transactional
    public int rowCount(String userId) {
        return (int) (items.count("userId", userId) + wishes.count("userId", userId));
    }

    /**
     * Serialises the whole account.
     *
     * <p>{@code @ActivateRequestContext} because the background pool of {@link ExportJobs}
     * calls this outside any request: without it Hibernate has no context to open its
     * session in. Inside a request the interceptor finds the context already active and
     * leaves it alone.
     *
     * @throws NotFoundException when the user has no {@code app_user} row — an account that
     *                           has never called the API has nothing to export
     */
    @ActivateRequestContext
    @Transactional
    public ExportFile build(String userId, ExportFormat format) {
        ExportDto document = collect(userId);
        byte[] content = format == ExportFormat.JSON ? writeJson(document)
                : ExportCsv.write(document);
        return new ExportFile(filename(format), format, content);
    }

    /** Reads everything the user owns, in a stable order. */
    ExportDto collect(String userId) {
        AppUser user = users.findById(userId);
        if (user == null) {
            throw new NotFoundException();
        }

        List<ExportCollectionItemDto> collection = items.listForExport(userId).stream()
                .map(item -> new ExportCollectionItemDto(
                        book(item.edition),
                        item.status,
                        item.rating,
                        item.review,
                        item.acquiredAt,
                        item.rankCategory != null ? item.rankCategory.code : null,
                        progress(item.progress)))
                .toList();

        List<ExportWishDto> wishlist = wishes.listForExport(userId).stream()
                .map(wish -> new ExportWishDto(book(wish.edition), wish.priority,
                        wish.estimatedPrice, wish.note))
                .toList();

        List<ExportCategoryDto> ownCategories = categories.listCustomForUser(userId).stream()
                .map(c -> new ExportCategoryDto(c.code, c.label, c.color, c.sortOrder))
                .toList();

        List<ExportGoalDto> readingGoals = goals.listByUser(userId).stream()
                .map(g -> new ExportGoalDto(g.year, g.targetCount, g.unit))
                .toList();

        List<ExportSeriesFollowDto> followed =
                series.listByIds(follows.followedSeriesIds(userId)).stream()
                        .map(s -> new ExportSeriesFollowDto(s.kind, s.title))
                        .sorted(Comparator.comparing((ExportSeriesFollowDto f) -> f.kind().name())
                                .thenComparing(f -> f.title().toLowerCase(Locale.ROOT)))
                        .toList();

        return new ExportDto(SCHEMA_VERSION, OffsetDateTime.now(), ExportUserDto.of(user),
                ownCategories, readingGoals, collection, wishlist, followed);
    }

    /**
     * The book as the user would type it in, which is exactly what an import takes: the
     * export deliberately carries no identifier <em>of this instance</em>, so that
     * re-importing it creates the rows it needs instead of pointing at rows that may not
     * exist in the target instance.
     *
     * <p>The provider reference is the one identifier it does carry, and for the same
     * reason: it names a record in a public catalog, which any instance can resolve. Dropping
     * it would make a backup and a restore lose exactly what #184 set out to stop losing.
     */
    private static ManualBookDto book(Edition edition) {
        Work work = edition.work;
        return new ManualBookDto(work.kind, work.title, work.authors, work.seriesTitle,
                work.volumeNumber, edition.isbn13, edition.publisher, edition.language,
                edition.pageCount, edition.coverUrl, edition.format, edition.releaseDate,
                work.originalYear, work.synopsis, work.genresText,
                edition.provider, edition.providerRef);
    }

    private static ExportProgressDto progress(ReadingProgress p) {
        return p == null ? null
                : new ExportProgressDto(p.currentPage, p.percent, p.startedAt, p.finishedAt);
    }

    /**
     * Pretty-printed on purpose: this file is handed to a human, who may well open it in a
     * text editor before deciding whether the service can be trusted with their library.
     */
    private byte[] writeJson(ExportDto document) {
        try {
            return json.writerWithDefaultPrettyPrinter().writeValueAsBytes(document);
        } catch (JsonProcessingException e) {
            throw new InternalServerErrorException("Could not serialise the export.", e);
        }
    }

    /** Name the browser saves the file under, dated so two exports do not overwrite. */
    static String filename(ExportFormat format) {
        return "librarius-export-" + LocalDate.now() + "." + format.extension;
    }
}
