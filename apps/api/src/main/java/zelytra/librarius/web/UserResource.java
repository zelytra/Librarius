package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.social.UserBlockService;
import zelytra.librarius.social.UserFollowService;

/**
 * Acting on another member: following (#200) and blocking (#203).
 *
 * <p>The only thing a caller does to another account here is follow, unfollow, block or
 * unblock it — no profile of somebody else is read through this resource, so there is nothing
 * to gate on the mutual-follow visibility rule the later issue (#201) adds. The caller's own
 * relationship lists live on {@code /api/me} instead, since they are metadata about the
 * caller, not about the target.
 *
 * <p>An unknown id answers 404 and targeting oneself answers 400, both decided in the
 * services. A block also overrides a follow: {@link #follow} is refused with 400 while a block
 * stands between the two accounts.
 */
@Path("/api/users")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class UserResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    UserFollowService follows;

    @Inject
    UserBlockService blocks;

    /** The caller starts following the member. Idempotent, 204. */
    @PUT
    @Path("/{id}/follow")
    public Response follow(@PathParam("id") String id) {
        currentUser.require();
        follows.follow(currentUser.id(), id);
        return Response.noContent().build();
    }

    /** The caller stops following the member. Idempotent, 204. */
    @DELETE
    @Path("/{id}/follow")
    public Response unfollow(@PathParam("id") String id) {
        follows.unfollow(currentUser.id(), id);
        return Response.noContent().build();
    }

    /**
     * The caller starts blocking the member. Idempotent, 204. The block hides content both
     * ways and severs any follow between the two accounts (#203).
     */
    @PUT
    @Path("/{id}/block")
    public Response block(@PathParam("id") String id) {
        currentUser.require();
        blocks.block(currentUser.id(), id);
        return Response.noContent().build();
    }

    /** The caller stops blocking the member. Idempotent, 204. */
    @DELETE
    @Path("/{id}/block")
    public Response unblock(@PathParam("id") String id) {
        blocks.unblock(currentUser.id(), id);
        return Response.noContent().build();
    }
}
