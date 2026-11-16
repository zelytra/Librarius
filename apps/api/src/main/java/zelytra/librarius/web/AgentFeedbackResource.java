package zelytra.librarius.web;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import zelytra.librarius.domain.Feedback.Status;
import zelytra.librarius.feedback.AgentKey;
import zelytra.librarius.feedback.FeedbackService;
import zelytra.librarius.web.ApiDtos.AgentFeedbackPageDto;
import zelytra.librarius.web.ApiDtos.FeedbackStatusDto;

import java.util.UUID;

/**
 * The reports for agents (#518): read in full and triaged by a script or an AI agent that
 * has no Keycloak session. Outside Keycloak ({@code @PermitAll}), but every call needs the
 * key configured as {@code librarius.agent.api-key}, sent in {@code X-Api-Key} — not in
 * {@code Authorization}, which the OIDC layer reads as a Keycloak token and refuses. 401
 * without it, and always while no key is configured.
 */
@Path("/api/agent/feedback")
@PermitAll
public class AgentFeedbackResource {

    static final String KEY_HEADER = "X-Api-Key";

    @Inject
    AgentKey key;

    @Inject
    FeedbackService feedback;

    /**
     * Every report in full, the latest seen first — only those in {@code status} when it is
     * given ({@code NEW}, {@code SEEN}, {@code HANDLED} or {@code IRRELEVANT}; 400 otherwise).
     * Reading leaves the statuses as they are.
     */
    @GET
    public AgentFeedbackPageDto list(@HeaderParam(KEY_HEADER) String presented,
            @QueryParam("page") @DefaultValue("0") int page, @QueryParam("size") @DefaultValue("50") int size,
            @QueryParam("status") String status) {
        requireKey(presented);
        return feedback.listInFull(PageRequest.of(page, size),
                status == null || status.isBlank() ? null : parse(status));
    }

    /** Sets where a report stands once the agent has dealt with it. 204, 404 for an unknown report. */
    @PUT
    @Path("/{id}/status")
    public void status(@HeaderParam(KEY_HEADER) String presented, @PathParam("id") UUID id,
            @Valid @NotNull FeedbackStatusDto body) {
        requireKey(presented);
        if (!feedback.setStatus(id, parse(body.status()))) {
            throw new NotFoundException();
        }
    }

    private void requireKey(String presented) {
        if (!key.matches(presented)) {
            throw new NotAuthorizedException("ApiKey");
        }
    }

    private static Status parse(String status) {
        try {
            return Status.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown status: " + status);
        }
    }
}
