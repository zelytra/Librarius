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
import zelytra.librarius.social.UserFollowService;

/**
 * Following another member (#200).
 *
 * <p>The only thing a caller does to another account here is follow or unfollow it — no
 * profile of somebody else is read through this resource, so there is nothing to gate on the
 * mutual-follow visibility rule the next issue (#201) adds. The caller's own relationship
 * lists live on {@code /api/me} instead, since they are metadata about the caller, not about
 * the target.
 *
 * <p>An unknown id answers 404 and following oneself answers 400, both decided in
 * {@link UserFollowService}.
 */
@Path("/api/users")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class UserResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    UserFollowService follows;

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
}
