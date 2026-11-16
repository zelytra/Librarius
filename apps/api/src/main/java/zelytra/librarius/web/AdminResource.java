package zelytra.librarius.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import zelytra.librarius.domain.Feedback.Status;
import zelytra.librarius.feedback.FeedbackService;
import zelytra.librarius.monitoring.MonitoringService;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.FeedbackDetailDto;
import zelytra.librarius.web.ApiDtos.FeedbackPageDto;
import zelytra.librarius.web.ApiDtos.FeedbackStatusDto;
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

    /**
     * Every report, the latest seen first — only those in {@code status} when it is given
     * ({@code NEW}, {@code SEEN}, {@code HANDLED} or {@code IRRELEVANT}, #495; 400 otherwise).
     */
    @GET
    @Path("/feedback")
    public FeedbackPageDto feedback(@QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size, @QueryParam("status") String status) {
        return feedback.list(PageRequest.of(page, size), status == null || status.isBlank() ? null : parse(status));
    }

    /** One report in full; opening a new one marks it seen (#495). */
    @GET
    @Path("/feedback/{id}")
    public FeedbackDetailDto feedback(@PathParam("id") UUID id) {
        return feedback.open(id).orElseThrow(NotFoundException::new);
    }

    /** Sets where an admin stands with a report (#495). 204, 404 for an unknown report. */
    @PUT
    @Path("/feedback/{id}/status")
    public void status(@PathParam("id") UUID id, @Valid @jakarta.validation.constraints.NotNull FeedbackStatusDto body) {
        if (!feedback.setStatus(id, parse(body.status()))) {
            throw new NotFoundException();
        }
    }

    private static Status parse(String status) {
        try {
            return Status.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown status: " + status);
        }
    }

    /** Reports and active members over the last 30 days, members active now, the services. */
    @GET
    @Path("/monitoring")
    public MonitoringDto monitoring() {
        return monitoring.snapshot();
    }
}
