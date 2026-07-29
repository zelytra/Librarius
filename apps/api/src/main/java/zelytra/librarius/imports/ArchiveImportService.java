package zelytra.librarius.imports;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import zelytra.librarius.catalog.CatalogEntryService;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.domain.RankCategory;
import zelytra.librarius.domain.ReadingGoal;
import zelytra.librarius.domain.ReadingProgress;
import zelytra.librarius.domain.Series;
import zelytra.librarius.domain.WishPriority;
import zelytra.librarius.domain.WishlistItem;
import zelytra.librarius.domain.Work;
import zelytra.librarius.domain.repository.LibraryItemRepository;
import zelytra.librarius.domain.repository.RankCategoryRepository;
import zelytra.librarius.domain.repository.ReadingGoalRepository;
import zelytra.librarius.domain.repository.ReadingProgressRepository;
import zelytra.librarius.domain.repository.SeriesFollowRepository;
import zelytra.librarius.domain.repository.SeriesRepository;
import zelytra.librarius.domain.repository.WishlistItemRepository;
import zelytra.librarius.export.ExportService;
import zelytra.librarius.imports.ImportService.ImportResult;
import zelytra.librarius.web.ApiDtos.ExportCategoryDto;
import zelytra.librarius.web.ApiDtos.ExportCollectionItemDto;
import zelytra.librarius.web.ApiDtos.ExportDto;
import zelytra.librarius.web.ApiDtos.ExportGoalDto;
import zelytra.librarius.web.ApiDtos.ExportProgressDto;
import zelytra.librarius.web.ApiDtos.ExportSeriesFollowDto;
import zelytra.librarius.web.ApiDtos.ExportWishDto;
import zelytra.librarius.web.ApiDtos.ManualBookDto;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Puts back what {@code GET /api/export?format=json} took out.
 *
 * <p>This is the other half of the portability promise: an export nobody can re-import is a
 * file, not a way out. Restoring into an emptied account has to give the library back
 * unchanged — that is what {@code ExportRoundTripTest} asserts, by comparing the document
 * produced before the wipe with the one produced after the restore.
 *
 * <p>It is <b>additive</b>, exactly like the CSV import next to it: a title already in the
 * collection is skipped rather than duplicated, so re-importing the same file twice is
 * harmless. Nothing here ever deletes — a restore that could empty a collection would be a
 * far more dangerous button than the one that fills it.
 */
@ApplicationScoped
public class ArchiveImportService {

    @Inject
    CatalogEntryService catalog;

    @Inject
    LibraryItemRepository items;

    @Inject
    WishlistItemRepository wishes;

    @Inject
    ReadingProgressRepository progresses;

    @Inject
    ReadingGoalRepository goals;

    @Inject
    RankCategoryRepository categories;

    @Inject
    SeriesRepository series;

    @Inject
    SeriesFollowRepository follows;

    @Inject
    MeterRegistry meters;

    /**
     * Restores a document into the caller's account.
     *
     * @return how many titles were created and how many were already there; the counters
     *         cover the collection and the wishlist, the rest of the document — goals,
     *         categories, follows — being brought back in step and not counted twice
     * @throws ImportException when the document was produced by a newer version, whose
     *                         fields this one would misread
     */
    @Transactional
    public ImportResult restore(String userId, ExportDto document) {
        if (document == null) {
            throw new ImportException("Fichier d'export illisible.");
        }
        if (document.schemaVersion() > ExportService.SCHEMA_VERSION) {
            throw new ImportException("Ce fichier vient d'une version plus récente de "
                    + "l'application et ne peut pas être importé ici.");
        }

        restoreCategories(userId, document.categories());
        restoreGoals(userId, document.goals());
        int imported = restoreCollection(userId, document.collection());
        int wishesImported = restoreWishlist(userId, document.wishlist());
        restoreFollows(userId, document.followedSeries());

        int total = size(document.collection()) + size(document.wishlist());
        int created = imported + wishesImported;
        meters.counter("librarius.import", "source", "json").increment(created);
        return new ImportResult("json", created, total - created, total);
    }

    // ── Ranks and goals ───────────────────────────────────────────────────────

    /**
     * Recreates the categories the user had invented. The built-ins are not in the document
     * — they belong to nobody — and are already there for every account.
     */
    private void restoreCategories(String userId, List<ExportCategoryDto> exported) {
        if (exported == null) {
            return;
        }
        Set<String> known = new HashSet<>();
        categories.listCustomForUser(userId).forEach(c -> known.add(c.code));
        for (ExportCategoryDto dto : exported) {
            if (dto.code() == null || dto.code().isBlank() || !known.add(dto.code())) {
                continue;
            }
            RankCategory category = new RankCategory();
            category.userId = userId;
            category.code = dto.code();
            category.label = dto.label() != null ? dto.label() : dto.code();
            category.color = dto.color();
            category.sortOrder = dto.sortOrder();
            category.builtin = false;
            categories.persist(category);
        }
    }

    /**
     * One goal per year, so an existing one is updated rather than duplicated.
     *
     * <p>The row is filled in <em>before</em> it is persisted, never after: the next read of
     * the session auto-flushes, and a goal persisted empty then completed reached PostgreSQL
     * with a null {@code target_count} and failed the whole restore on a not-null
     * constraint.
     */
    private void restoreGoals(String userId, List<ExportGoalDto> exported) {
        if (exported == null) {
            return;
        }
        for (ExportGoalDto dto : exported) {
            // Same floor as GoalUpsertDto: a goal of zero titles is not a goal, and the
            // column would happily take it.
            if (dto.targetCount() < 1) {
                continue;
            }
            ReadingGoal goal = goals.findByUserAndYear(userId, dto.year()).orElse(null);
            boolean isNew = goal == null;
            if (isNew) {
                goal = new ReadingGoal();
                goal.userId = userId;
                goal.year = dto.year();
            }
            goal.targetCount = dto.targetCount();
            if (dto.unit() != null) {
                goal.unit = dto.unit();
            }
            if (isNew) {
                goals.persist(goal);
            }
        }
    }

    // ── Titles ────────────────────────────────────────────────────────────────

    private int restoreCollection(String userId, List<ExportCollectionItemDto> exported) {
        if (exported == null) {
            return 0;
        }
        Set<String> existing = new HashSet<>();
        for (LibraryItem item : items.listForExport(userId)) {
            existing.add(key(item.edition));
        }

        int created = 0;
        for (ExportCollectionItemDto dto : exported) {
            ManualBookDto book = dto.book();
            if (book == null || book.title() == null || book.title().isBlank()) {
                continue;
            }
            if (!existing.add(key(book))) {
                continue;
            }
            Edition edition = catalog.createManualEdition(book);
            LibraryItem item = new LibraryItem();
            item.userId = userId;
            item.edition = edition;
            item.status = dto.status() != null ? dto.status() : LibraryStatus.OWNED;
            item.rating = dto.rating();
            item.review = dto.review();
            item.acquiredAt = dto.acquiredAt();
            item.rankCategory = categories.findForUserByCode(userId, dto.rankCode()).orElse(null);
            items.persist(item);
            restoreProgress(item, dto.progress());
            created++;
        }
        return created;
    }

    private void restoreProgress(LibraryItem item, ExportProgressDto dto) {
        if (dto == null) {
            return;
        }
        ReadingProgress progress = new ReadingProgress();
        progress.libraryItem = item;
        progress.currentPage = dto.currentPage();
        progress.percent = dto.percent();
        progress.startedAt = dto.startedAt();
        progress.finishedAt = dto.finishedAt();
        progresses.persist(progress);
    }

    private int restoreWishlist(String userId, List<ExportWishDto> exported) {
        if (exported == null) {
            return 0;
        }
        Set<String> existing = new HashSet<>();
        for (WishlistItem wish : wishes.listForExport(userId)) {
            existing.add(key(wish.edition));
        }

        int created = 0;
        for (ExportWishDto dto : exported) {
            ManualBookDto book = dto.book();
            if (book == null || book.title() == null || book.title().isBlank()) {
                continue;
            }
            if (!existing.add(key(book))) {
                continue;
            }
            WishlistItem wish = new WishlistItem();
            wish.userId = userId;
            wish.edition = catalog.createManualEdition(book);
            wish.priority = dto.priority() != null ? dto.priority() : WishPriority.SOON;
            wish.estimatedPrice = dto.estimatedPrice();
            wish.note = dto.note();
            wishes.persist(wish);
            created++;
        }
        return created;
    }

    /**
     * Follows are named, not referenced: the document carries {@code (kind, title)}, which is
     * the unique key of the shared {@code series} row. A run nobody has entered yet is
     * created here, exactly as adding one of its volumes would.
     */
    private void restoreFollows(String userId, List<ExportSeriesFollowDto> exported) {
        if (exported == null) {
            return;
        }
        for (ExportSeriesFollowDto dto : exported) {
            if (dto.kind() == null || dto.title() == null || dto.title().isBlank()) {
                continue;
            }
            Series run = series.findByKindAndTitle(dto.kind(), dto.title()).orElseGet(() -> {
                Series created = new Series();
                created.kind = dto.kind();
                created.title = dto.title().trim();
                series.persist(created);
                return created;
            });
            follows.follow(userId, run.id);
        }
    }

    private static int size(List<?> list) {
        return list == null ? 0 : list.size();
    }

    /**
     * What makes two entries the same book for the purposes of not importing it twice.
     *
     * <p>It identifies an <b>edition</b>, not a work. The volume number is in it because
     * without it restoring a manga run would keep the first volume and drop the other
     * twenty-nine; the ISBN, the publisher and the format are in it because a work can be
     * owned in several editions (#152), and a key stopping at the title would restore one of
     * them and silently swallow the rest.
     */
    private static String key(ManualBookDto book) {
        return String.join("|",
                trimmed(book.title()),
                trimmed(book.authors()),
                book.volumeNumber() == null ? "" : book.volumeNumber().toString(),
                trimmed(book.isbn13()),
                trimmed(book.publisher()),
                trimmed(book.format()))
                .toLowerCase(Locale.FRENCH);
    }

    /** The same key, read off a row already in the collection. */
    private static String key(Edition edition) {
        Work work = edition.work;
        return String.join("|",
                trimmed(work.title),
                trimmed(work.authors),
                work.volumeNumber == null ? "" : work.volumeNumber.toString(),
                trimmed(edition.isbn13),
                trimmed(edition.publisher),
                trimmed(edition.format))
                .toLowerCase(Locale.FRENCH);
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
