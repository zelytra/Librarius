package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import zelytra.librarius.domain.repository.LibraryItemRepository;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.GenreCount;

import java.util.List;

/**
 * The genres present in the caller's collection, most frequent first.
 *
 * <p>What {@code /api/library?genre=} can usefully be called with: the whole {@code genre}
 * table would list genres the user owns nothing of, and would leak what other users
 * collect. The statistics expose the same figures, capped at the six the breakdown shows.
 */
@Path("/api/genres")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class GenreResource {

    /** A filter list, not a catalog dump: a collection spanning more genres is unheard of. */
    private static final int MAX_GENRES = 200;

    @Inject
    CurrentUser currentUser;

    @Inject
    LibraryItemRepository library;

    @GET
    public List<GenreCount> list() {
        return library.topGenres(currentUser.id(), MAX_GENRES).stream()
                .map(GenreCount::of)
                .toList();
    }
}
