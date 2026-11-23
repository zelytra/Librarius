package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.social.NotificationService;
import zelytra.librarius.web.ApiDtos.NotificationPageDto;
import zelytra.librarius.web.ApiDtos.UnreadCountDto;

/** The caller's own notifications (#516, #211): the bell's list, its badge, and reading them. */
@Path("/api/notifications")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class NotificationResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    NotificationService notifications;

    /** Newest first. */
    @GET
    public NotificationPageDto list(@QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("30") int size) {
        currentUser.require();
        return notifications.list(currentUser.id(), PageRequest.of(page, size));
    }

    /** How many are unread: the bell's badge. */
    @GET
    @Path("/unread")
    public UnreadCountDto unread() {
        currentUser.require();
        return new UnreadCountDto(notifications.unread(currentUser.id()));
    }

    /** Deletes one of the caller's notifications (#629). 204; 404 when it is not theirs. */
    @jakarta.ws.rs.DELETE
    @Path("/{id}")
    public void delete(@jakarta.ws.rs.PathParam("id") java.util.UUID id) {
        currentUser.require();
        if (!notifications.delete(currentUser.id(), id)) {
            throw new jakarta.ws.rs.NotFoundException();
        }
    }

    /** Deletes every notification of the caller (#629). 204. */
    @jakarta.ws.rs.DELETE
    public void deleteAll() {
        currentUser.require();
        notifications.deleteAll(currentUser.id());
    }

    /** Marks every notification read. 204. */
    @POST
    @Path("/read")
    public void readAll() {
        currentUser.require();
        notifications.readAll(currentUser.id());
    }
}
