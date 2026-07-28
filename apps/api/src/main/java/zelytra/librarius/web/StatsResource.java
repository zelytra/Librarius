package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
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

        Integer goalTarget = goals.findByUserAndYear(userId, Year.now().getValue())
                .map(goal -> goal.targetCount)
                .orElse(null);

        // Progress towards the goal is currently the number of titles read, all years
        // taken together — same value as before, the goal unit is not applied yet.
        return new StatsDto(totals.read(), totals.reading(), totals.toRead(), totals.pagesRead(),
                seriesCount, goalTarget, totals.read(), byGenre);
    }
}
