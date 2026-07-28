package zelytra.librarius.stats;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import zelytra.librarius.domain.GoalUnit;
import zelytra.librarius.domain.ReadingGoal;
import zelytra.librarius.domain.repository.LibraryItemRepository;
import zelytra.librarius.domain.repository.LibraryItemRepository.StatusTotals;
import zelytra.librarius.domain.repository.ReadingGoalRepository;
import zelytra.librarius.domain.repository.ReadingProgressRepository;
import zelytra.librarius.web.ApiDtos.GenreCount;
import zelytra.librarius.web.ApiDtos.StatsDto;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;

/**
 * Assembles the reading statistics.
 *
 * <p>Everything is aggregated by the database in a bounded number of queries: loading a
 * library to fold it in memory made the cost of this endpoint — hit on every Home and Stats
 * render — grow with the number of titles.
 */
@ApplicationScoped
public class StatsService {

    /** Number of genres shown in the breakdown. */
    private static final int TOP_GENRES = 6;

    @Inject
    LibraryItemRepository library;

    @Inject
    ReadingGoalRepository goals;

    @Inject
    ReadingProgressRepository progresses;

    /** Point-in-time counters, plus where the user stands against this year's goal. */
    public StatsDto stats(String userId) {
        StatusTotals totals = library.statusTotals(userId);
        long seriesCount = library.countDistinctSeries(userId);
        List<GenreCount> byGenre = library.topGenres(userId, TOP_GENRES).stream()
                .map(GenreCount::of)
                .toList();

        int year = Year.now().getValue();
        ReadingGoal goal = goals.findByUserAndYear(userId, year).orElse(null);
        // Without a goal there is no unit to count in, and the figure is only shown next to
        // a target anyway: titles finished this year is the sensible neutral answer.
        GoalUnit unit = goal != null ? goal.unit : GoalUnit.BOOKS;
        long goalCurrent = progresses.progressTowards(
                userId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), unit);

        return new StatsDto(totals.read(), totals.reading(), totals.toRead(), totals.pagesRead(),
                seriesCount,
                goal != null ? goal.targetCount : null,
                goal != null ? goal.unit.name() : null,
                goalCurrent, byGenre);
    }
}
