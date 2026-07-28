package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import zelytra.librarius.domain.GoalUnit;
import zelytra.librarius.domain.ReadingGoal;
import zelytra.librarius.domain.repository.LibraryItemRepository;
import zelytra.librarius.domain.repository.LibraryItemRepository.StatusTotals;
import zelytra.librarius.domain.repository.ReadingGoalRepository;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.GenreCount;
import zelytra.librarius.web.ApiDtos.StatsDto;

import java.time.Year;
import java.util.List;

/**
 * The user's aggregated reading statistics.
 *
 * <p>Everything is aggregated by the database in a bounded number of queries. Loading the
 * whole collection to fold it in Java made the cost of this endpoint — called on every
 * Home and Stats render — grow linearly with the number of titles.
 */
@Path("/api/stats")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class StatsResource {

    /** Number of genres shown in the breakdown. */
    private static final int TOP_GENRES = 6;

    @Inject
    CurrentUser currentUser;

    @Inject
    LibraryItemRepository library;

    @Inject
    ReadingGoalRepository goals;

    @GET
    public StatsDto stats() {
        String userId = currentUser.id();

        StatusTotals totals = library.statusTotals(userId);
        long seriesCount = library.countDistinctSeries(userId);
        List<GenreCount> byGenre = library.topGenres(userId, TOP_GENRES).stream()
                .map(GenreCount::of)
                .toList();

        int year = Year.now().getValue();
        ReadingGoal goal = goals.findByUserAndYear(userId, year).orElse(null);

        // Progress is measured over the year and in the goal's own unit — a goal is annual,
        // so a lifetime count would show a 30-book target already met by someone who read
        // 30 books over ten years. With no goal set the figure still means something: how
        // much has been read this year, in books, which is what the invitation to set one
        // shows.
        GoalUnit unit = goal != null ? goal.unit : GoalUnit.BOOKS;
        long goalCurrent = library.readInYear(userId, year, unit);

        return new StatsDto(totals.read(), totals.reading(), totals.toRead(), totals.pagesRead(),
                seriesCount, goal != null ? goal.targetCount : null, goalCurrent,
                goal != null ? goal.unit.name() : null, byGenre);
    }
}
