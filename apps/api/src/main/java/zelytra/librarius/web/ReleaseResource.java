package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.releases.UpcomingReleaseService;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.UpcomingReleaseDto;

import java.util.List;
import java.util.UUID;

/**
 * What is coming out, for the caller.
 *
 * <p>Distinct from {@code /api/catalog/upcoming}, which answers the same global provider
 * trends to everybody: this one is built from the series the caller owns a volume of, has a
 * wish on, or follows, and returns nothing about any other run. It charges no provider
 * quota — the announcements are read from {@code upcoming_release}, which
 * {@code UpcomingReleaseRefresher} fills in the background.
 */
@Path("/api/releases")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class ReleaseResource {

    /** Ceiling on a page of announcements, matching the catalog endpoints. */
    private static final int MAX_LIMIT = 50;

    @Inject
    CurrentUser currentUser;

    @Inject
    UpcomingReleaseService releases;

    /**
     * Announcements still ahead of the caller, soonest first, undated ones last.
     *
     * <p>An empty list is the normal answer for somebody who follows nothing and collects
     * nothing — the screen invites them to follow a series rather than showing an empty
     * section.
     *
     * @param kind     restrict to books or to mangas; both when omitted
     * @param seriesId one of the caller's runs (#614) — the Series screen
     * @param authorId the runs of one author, stake or not (#614) — the Author screen
     * @param limit    how many announcements at most, clamped to 1–50
     */
    @GET
    @Path("/upcoming")
    public List<UpcomingReleaseDto> upcoming(@QueryParam("kind") Kind kind,
            @QueryParam("seriesId") UUID seriesId, @QueryParam("authorId") UUID authorId,
            @QueryParam("limit") @DefaultValue("20") int limit) {
        return releases.listForUser(currentUser.id(), kind, seriesId, authorId,
                Math.clamp(limit, 1, MAX_LIMIT));
    }
}
