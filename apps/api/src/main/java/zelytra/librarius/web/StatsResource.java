package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import zelytra.librarius.domain.repository.ReadingProgressRepository.TimelineGranularity;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.stats.StatsService;
import zelytra.librarius.web.ApiDtos.StatsDto;
import zelytra.librarius.web.ApiDtos.TimelineDto;

import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeParseException;

/**
 * The user's aggregated reading statistics.
 *
 * <p>Everything is aggregated by the database — see {@link StatsService}. Both endpoints
 * are hit on every Home and Stats render, so their cost must not follow the number of
 * titles in the collection.
 */
@Path("/api/stats")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class StatsResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    StatsService stats;

    @GET
    public StatsDto stats() {
        return stats.stats(currentUser.id());
    }

    /**
     * Reading over time, built on the day each title was finished.
     *
     * <p>The window defaults to the current calendar year, which is what both the goal
     * gauge and the yearly charts are about. Only the buckets holding something come back:
     * padding a range out to its every month would make the answer grow with the range
     * asked for rather than with the data behind it, and is a rendering concern.
     *
     * @param from        first day counted, ISO {@code yyyy-MM-dd}, 1 January by default
     * @param to          last day counted, inclusive, 31 December by default
     * @param granularity {@code month} (default) or {@code year}
     * @throws BadRequestException on an unparseable date, an unknown granularity, or a
     *                             window that ends before it starts
     */
    @GET
    @Path("/timeline")
    public TimelineDto timeline(
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @QueryParam("granularity") String granularity) {

        int year = Year.now().getValue();
        LocalDate start = parseDate("from", from, LocalDate.of(year, 1, 1));
        LocalDate end = parseDate("to", to, LocalDate.of(year, 12, 31));
        if (end.isBefore(start)) {
            throw new BadRequestException("The window ends before it starts: " + start + " > " + end);
        }
        TimelineGranularity bucket = TimelineGranularity.parse(granularity)
                .orElseThrow(() -> new BadRequestException("Unknown granularity: " + granularity));

        return stats.timeline(currentUser.id(), start, end, bucket);
    }

    /**
     * Reads an ISO date query parameter. Dates are parsed here rather than declared as
     * {@code LocalDate} parameters so that a typo answers 400 with the offending value
     * instead of the framework's own message.
     */
    private static LocalDate parseDate(String name, String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Invalid " + name + " date: " + value);
        }
    }
}
