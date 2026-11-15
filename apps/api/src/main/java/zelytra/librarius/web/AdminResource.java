package zelytra.librarius.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import zelytra.librarius.feedback.FeedbackService;
import zelytra.librarius.monitoring.MonitoringService;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.FeedbackDetailDto;
import zelytra.librarius.web.ApiDtos.FeedbackPageDto;
import zelytra.librarius.web.ApiDtos.MonitoringDto;

import java.util.UUID;

/**
 * The admin screens' data (#488): the reports (#489, #490) and the monitoring (#491). Every
 * endpoint needs the Keycloak realm role {@code admin}; anyone else gets a 403.
 */
@Path("/api/admin")
@RolesAllowed(CurrentUser.ADMIN)
public class AdminResource {

    @Inject
    FeedbackService feedback;

    @Inject
    MonitoringService monitoring;

    /** Every report, the latest seen first. */
    @GET
    @Path("/feedback")
    public FeedbackPageDto feedback(@QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size) {
        return feedback.list(PageRequest.of(page, size));
    }

    @GET
    @Path("/feedback/{id}")
    public FeedbackDetailDto feedback(@PathParam("id") UUID id) {
        return feedback.get(id).orElseThrow(NotFoundException::new);
    }

    /** Reports and active members over the last 30 days, members active now, the services. */
    @GET
    @Path("/monitoring")
    public MonitoringDto monitoring() {
        return monitoring.snapshot();
    }
}
