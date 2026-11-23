package zelytra.librarius.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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

    @Inject
    zelytra.librarius.contribution.CatalogEditService catalogEdits;

    @Inject
    zelytra.librarius.report.ReportService catalogReports;

    @Inject
    zelytra.librarius.releases.UpcomingReleaseService releases;

    /**
     * Enters a checked French release date (#614) — 201 with the announcement; 400 incomplete,
     * 404 for an unknown series. It replaces whatever was announced for that volume in France.
     */
    @POST
    @Path("/releases")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response curateRelease(@Valid @jakarta.validation.constraints.NotNull ApiDtos.CurateReleaseDto body) {
        return releases.curate(body)
                .map(release -> Response.status(Response.Status.CREATED).entity(release).build())
                .orElseThrow(NotFoundException::new);
    }

    /** Withdraws an announcement (#614) — 204; 404 when there is none under that id. */
    @DELETE
    @Path("/releases/{id}")
    public void withdrawRelease(@PathParam("id") UUID id) {
        if (!releases.withdraw(id)) {
            throw new NotFoundException();
        }
    }

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

    /**
     * The catalog edit proposals (#618), newest first — only those in {@code status} when it is
     * given ({@code PENDING}, {@code APPLIED}, {@code REJECTED}, {@code REVERTED}; 400 otherwise).
     */
    @GET
    @Path("/catalog-edits")
    public ApiDtos.CatalogEditPageDto catalogEdits(@QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size, @QueryParam("status") String status) {
        return catalogEdits.list(status == null || status.isBlank() ? null
                : parse(zelytra.librarius.domain.CatalogEditStatus.class, status), PageRequest.of(page, size));
    }

    /** Applies a pending proposal to the catalog. 404 unknown, 409 when it is not pending. */
    @jakarta.ws.rs.POST
    @Path("/catalog-edits/{id}/approve")
    public ApiDtos.CatalogEditDto approve(@PathParam("id") UUID id, @Valid ApiDtos.CatalogEditDecisionDto body) {
        return catalogEdits.approve(currentUser.id(), id, body == null ? null : body.note());
    }

    /** Refuses a pending proposal; nothing is written. 404 unknown, 409 when it is not pending. */
    @jakarta.ws.rs.POST
    @Path("/catalog-edits/{id}/reject")
    public ApiDtos.CatalogEditDto reject(@PathParam("id") UUID id, @Valid ApiDtos.CatalogEditDecisionDto body) {
        return catalogEdits.reject(currentUser.id(), id, body == null ? null : body.note());
    }

    /** Puts back what an applied proposal replaced. 404 unknown, 409 when it is not applied. */
    @jakarta.ws.rs.POST
    @Path("/catalog-edits/{id}/revert")
    public ApiDtos.CatalogEditDto revert(@PathParam("id") UUID id, @Valid ApiDtos.CatalogEditDecisionDto body) {
        return catalogEdits.revert(currentUser.id(), id, body == null ? null : body.note());
    }

    /**
     * The reports filed from the book screens (#621) — not the bug reports of {@code /feedback} —
     * newest first, only those in {@code status} when given ({@code OPEN}, {@code UPHELD},
     * {@code DISMISSED}; 400 otherwise).
     */
    @GET
    @Path("/reports")
    public ApiDtos.AdminReportPageDto reports(@QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size, @QueryParam("status") String status) {
        return catalogReports.list(status == null || status.isBlank() ? null
                : parse(zelytra.librarius.domain.ReportStatus.class, status), PageRequest.of(page, size));
    }

    /** Upholds or dismisses a report — what the trust revocation (#195) counts. 204, 404 unknown. */
    @PUT
    @Path("/reports/{id}/status")
    public void reportStatus(@PathParam("id") UUID id, @Valid @jakarta.validation.constraints.NotNull ApiDtos.ReportStatusDto body) {
        if (!catalogReports.setStatus(id, parse(zelytra.librarius.domain.ReportStatus.class, body.status()))) {
            throw new NotFoundException();
        }
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown status: " + value);
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
     * How a member's trust is decided (#625): {@code AUTO} (the daily evaluation), {@code TRUSTED}
     * or {@code UNTRUSTED} (kept so by an admin), applied at once. 204; 400 another mode, 404 unknown.
     */
    @PUT
    @Path("/users/{id}/trust")
    public void trust(@PathParam("id") String id, @Valid @jakarta.validation.constraints.NotNull ApiDtos.TrustModeDto body) {
        if (!users.setTrust(id, parse(zelytra.librarius.account.AdminUserService.TrustMode.class, body.mode()))) {
            throw new NotFoundException();
        }
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
