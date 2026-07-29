package zelytra.librarius.stats;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import zelytra.librarius.domain.GoalUnit;
import zelytra.librarius.domain.ReadingGoal;
import zelytra.librarius.domain.repository.LibraryItemRepository;
import zelytra.librarius.domain.repository.LibraryItemRepository.StatusTotals;
import zelytra.librarius.domain.repository.ReadingGoalRepository;
import zelytra.librarius.domain.repository.ReadingProgressRepository;
import zelytra.librarius.domain.repository.ReadingProgressRepository.Breakdown;
import zelytra.librarius.domain.repository.ReadingProgressRepository.PeriodTotal;
import zelytra.librarius.domain.repository.ReadingProgressRepository.TimelineGranularity;
import zelytra.librarius.web.ApiDtos.BreakdownCountDto;
import zelytra.librarius.web.ApiDtos.GenreCount;
import zelytra.librarius.web.ApiDtos.StatsDto;
import zelytra.librarius.web.ApiDtos.TimelineDto;
import zelytra.librarius.web.ApiDtos.TimelinePointDto;

import java.time.LocalDate;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Assembles the reading statistics.
 *
 * <p>Everything is aggregated by the database in a bounded number of queries. What is left
 * to Java only ever walks the buckets that come back — at most twelve for a year of
 * months — never the collection behind them: loading a library to fold it in memory made
 * the cost of these endpoints grow with the number of titles.
 */
@ApplicationScoped
public class StatsService {

    /** Number of genres shown in the breakdown. */
    private static final int TOP_GENRES = 6;

    /** Number of labels kept in each timeline breakdown. */
    private static final int TOP_LABELS = 6;

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

        return new StatsDto(totals.read(), totals.reading(), totals.toRead(), totals.abandoned(),
                totals.pagesRead(), seriesCount,
                goal != null ? goal.targetCount : null,
                goal != null ? goal.unit.name() : null,
                goalCurrent, byGenre);
    }

    /**
     * Reading over a window: the buckets, what they add up to, and the derived figures the
     * Stats screen puts next to them.
     *
     * @param from first day counted, inclusive
     * @param to   last day counted, inclusive
     */
    public TimelineDto timeline(String userId, LocalDate from, LocalDate to,
            TimelineGranularity granularity) {
        List<PeriodTotal> buckets = progresses.timeline(userId, from, to, granularity);

        long books = buckets.stream().mapToLong(PeriodTotal::books).sum();
        long pages = buckets.stream().mapToLong(PeriodTotal::pages).sum();
        PeriodTotal best = buckets.stream().max(Comparator.comparingLong(PeriodTotal::books))
                .orElse(null);
        OptionalDouble daysPerBook = progresses.averageDaysToFinish(userId, from, to);

        return new TimelineDto(from, to, granularity.name(),
                buckets.stream().map(TimelinePointDto::of).toList(),
                books, pages, pagesPerDay(pages, from, to),
                daysPerBook.isPresent() ? daysPerBook.getAsDouble() : null,
                best != null ? TimelinePointDto.of(best).period() : null,
                best != null ? best.books() : 0,
                breakdown(userId, from, to, Breakdown.AUTHOR),
                breakdown(userId, from, to, Breakdown.PUBLISHER),
                breakdown(userId, from, to, Breakdown.LANGUAGE),
                breakdown(userId, from, to, Breakdown.RANK));
    }

    private List<BreakdownCountDto> breakdown(String userId, LocalDate from, LocalDate to,
            Breakdown dimension) {
        return progresses.breakdown(userId, from, to, dimension, TOP_LABELS).stream()
                .map(BreakdownCountDto::of)
                .toList();
    }

    /**
     * Reading pace over the window, in pages per day.
     *
     * <p>Divided by the days that have actually gone by, not by the length of the window:
     * the current year is the range the screen asks for by default, and dividing eleven
     * months of reading by twelve months of calendar would report a pace nobody read at.
     */
    static double pagesPerDay(long pages, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        LocalDate last = to.isAfter(today) ? today : to;
        if (last.isBefore(from)) {
            // The window has not started yet: no elapsed day to divide by.
            return 0;
        }
        long days = ChronoUnit.DAYS.between(from, last) + 1;
        return (double) pages / days;
    }
}
