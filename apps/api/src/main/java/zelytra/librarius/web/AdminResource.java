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

import java.util.List;
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

    @Inject
    zelytra.librarius.account.AdminUserService users;

    @Inject
    zelytra.librarius.account.AccountDeletionService deletion;

    @Inject
    CurrentUser currentUser;

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

    /** The members, newest first (#499); {@code q} narrows on username, display name or email. */
    @GET
    @Path("/users")
    public ApiDtos.AdminUserPageDto users(@QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size, @QueryParam("q") String q) {
        return users.list(PageRequest.of(page, size), q);
    }

    /**
     * Deletes a member and everything they own, their Keycloak account first (#499) — the
     * erasure a member runs on themself. 204; 400 for the caller (Settings is their way), 404
     * unknown, 503 when Keycloak refuses and nothing was erased.
     */
    @jakarta.ws.rs.DELETE
    @Path("/users/{id}")
    public void deleteUser(@PathParam("id") String id) {
        if (id.equals(currentUser.id())) {
            throw new BadRequestException("An admin deletes their own account from Settings.");
        }
        if (!users.exists(id)) {
            throw new NotFoundException();
        }
        deletion.delete(id);
    }

    /**
     * How many members said they live in each country (#573), the largest first: a count per
     * country only, from the country each member filled themselves — members who did not are
     * not counted.
     */
    @GET
    @Path("/countries")
    public List<ApiDtos.CountryCountDto> countries() {
        return users.countries();
    }

    /** Reports and active members over the last 30 days, members active now, the services. */
    @GET
    @Path("/monitoring")
    public MonitoringDto monitoring() {
        return monitoring.snapshot();
    }
}
