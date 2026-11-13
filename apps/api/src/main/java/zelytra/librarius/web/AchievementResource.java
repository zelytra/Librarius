package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import zelytra.librarius.achievement.AchievementService;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.AchievementsDto;

/** The caller's achievements, XP and level (#287). Read-only for the client: unlocks are the server's. */
@Path("/api/achievements")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class AchievementResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    AchievementService achievements;

    /** Every achievement with its progress, the XP and level, and the unlocks not shown yet. */
    @GET
    public AchievementsDto overview() {
        currentUser.require();
        return achievements.overview(currentUser.id());
    }

    /** The reader was shown their new unlocks: they stop being reported as new. */
    @POST
    @Path("/seen")
    public Response seen() {
        achievements.markSeen(currentUser.id());
        return Response.noContent().build();
    }
}
