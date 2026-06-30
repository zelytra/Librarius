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
import zelytra.librarius.domain.WishPriority;
import zelytra.librarius.domain.WishlistItem;
import zelytra.librarius.domain.Work;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Objets de transfert (DTO) de l'API REST, regroupés pour rester compacts. */
public final class ApiDtos {

    private ApiDtos() {
    }

    public record MeDto(String id, String email, String displayName, String locale) {
        public static MeDto of(AppUser u) {
            return new MeDto(u.id, u.email, u.displayName, u.locale);
        }
    }

    /** Vue « livre » dénormalisée (œuvre + édition) renvoyée au front. */
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

    /** Saisie manuelle d'un ouvrage (avant l'intégration du catalogue externe). */
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
}
