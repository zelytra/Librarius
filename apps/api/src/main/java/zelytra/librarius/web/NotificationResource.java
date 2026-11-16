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

    /** Marks every notification read. 204. */
    @POST
    @Path("/read")
    public void readAll() {
        currentUser.require();
        notifications.readAll(currentUser.id());
    }
}
