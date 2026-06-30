package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.domain.repository.LibraryItemRepository;
import zelytra.librarius.domain.repository.ReadingGoalRepository;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.GenreCount;
import zelytra.librarius.web.ApiDtos.StatsDto;

import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Statistiques de lecture agrégées de l'utilisateur. */
@Path("/api/stats")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class StatsResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    LibraryItemRepository library;

    @Inject
    ReadingGoalRepository goals;

    @GET
    public StatsDto stats() {
        String userId = currentUser.id();
        List<LibraryItem> items = library.listByUser(userId);

        long read = 0;
        long reading = 0;
        long toRead = 0;
        long pagesRead = 0;
        var series = new java.util.HashSet<String>();
        Map<String, Long> genres = new LinkedHashMap<>();

        for (LibraryItem it : items) {
            switch (it.status) {
                case READ -> {
                    read++;
                    Integer pages = it.edition.pageCount;
                    if (pages != null) {
                        pagesRead += pages;
                    }
                }
                case READING -> reading++;
                case OWNED -> toRead++;
            }
            String s = it.edition.work.seriesTitle;
            if (s != null && !s.isBlank()) {
                series.add(s.toLowerCase());
            }
            String g = it.edition.work.genres;
            if (g != null && !g.isBlank()) {
                genres.merge(g, 1L, Long::sum);
            }
        }

        int year = Year.now().getValue();
        var goal = goals.findByUserAndYear(userId, year).orElse(null);
        Integer goalTarget = goal != null ? goal.targetCount : null;

        List<GenreCount> byGenre = genres.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(6)
                .map(e -> new GenreCount(e.getKey(), e.getValue()))
                .toList();

        return new StatsDto(read, reading, toRead, pagesRead, series.size(), goalTarget, read, byGenre);
    }
}
