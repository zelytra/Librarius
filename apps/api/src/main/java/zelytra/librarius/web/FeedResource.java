package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.social.FeedService;
import zelytra.librarius.web.ApiDtos.FeedCommentCreateDto;
import zelytra.librarius.web.ApiDtos.FeedCommentCreatedDto;
import zelytra.librarius.web.ApiDtos.FeedPageDto;
import zelytra.librarius.web.ApiDtos.FeedReactionDto;

import java.util.UUID;

/**
 * The social feed (#209, #210): what the members the caller follows did, newest first, and the
 * caller's reactions and comments on it. An event the caller may not see — unknown, or whose
 * author stopped being visible to them — answers 404 to every write, the #201 convention.
 */
@Path("/api/feed")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class FeedResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    FeedService feed;

    @GET
    public FeedPageDto feed(@QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        currentUser.require();
        return feed.feed(currentUser.id(), PageRequest.of(page, size));
    }

    /** One event with its reactions and comments — the screen a notification opens (#516). 404 out of reach. */
    @GET
    @Path("/{eventId}")
    public ApiDtos.FeedItemDto one(@PathParam("eventId") UUID eventId) {
        currentUser.require();
        return feed.one(currentUser.id(), eventId).orElseThrow(NotFoundException::new);
    }

    /** Sets the caller's reaction, replacing the one they had. 204. */
    @PUT
    @Path("/{eventId}/reaction")
    @Consumes(MediaType.APPLICATION_JSON)
    public void react(@PathParam("eventId") UUID eventId, @Valid @NotNull FeedReactionDto dto) {
        currentUser.require();
        if (!feed.react(currentUser.id(), eventId, dto.emote())) {
            throw new NotFoundException();
        }
    }

    /** Takes the caller's reaction back. 204. */
    @DELETE
    @Path("/{eventId}/reaction")
    public void unreact(@PathParam("eventId") UUID eventId) {
        currentUser.require();
        if (!feed.unreact(currentUser.id(), eventId)) {
            throw new NotFoundException();
        }
    }

    /** Adds a comment. 201. */
    @POST
    @Path("/{eventId}/comments")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response comment(@PathParam("eventId") UUID eventId, @Valid @NotNull FeedCommentCreateDto dto) {
        currentUser.require();
        UUID id = feed.comment(currentUser.id(), eventId, dto.comment()).orElseThrow(NotFoundException::new);
        return Response.status(Response.Status.CREATED).entity(new FeedCommentCreatedDto(id)).build();
    }

    /** Deletes one of the caller's own comments. 204; 404 for anyone else's. */
    @DELETE
    @Path("/{eventId}/comments/{commentId}")
    public void deleteComment(@PathParam("eventId") UUID eventId, @PathParam("commentId") UUID commentId) {
        if (!feed.deleteComment(currentUser.id(), eventId, commentId)) {
            throw new NotFoundException();
        }
    }
}
