package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import zelytra.librarius.domain.Feedback.Kind;
import zelytra.librarius.domain.Feedback.Source;
import zelytra.librarius.feedback.FeedbackService;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.ClientErrorDto;
import zelytra.librarius.web.ApiDtos.FeedbackCreateDto;
import zelytra.librarius.web.ApiDtos.FeedbackCreatedDto;

/**
 * What a member reports from the bug button (#489), and the errors the web app records by
 * itself (#490). Both go to the admins only: nothing here reads a report back.
 */
@Path("/api/feedback")
@Authenticated
public class FeedbackResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    FeedbackService feedback;

    @Inject
    zelytra.librarius.security.ActionQuota quota;

    /** Reports and error notices a caller may send in an hour (#583): each one writes a row. */
    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "librarius.quota.feedback.per-hour", defaultValue = "20")
    int reportsPerHour;

    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "librarius.quota.errors.per-hour", defaultValue = "120")
    int errorsPerHour;

    @POST
    public Response submit(@Valid FeedbackCreateDto dto) {
        String userId = currentUser.require().id;
        quota.enforce("feedback", userId, reportsPerHour, "Trop de signalements en peu de temps. Réessaie dans une heure.");
        var id = feedback.submit(userId, Kind.valueOf(dto.kind()), dto.message(), dto.page(), dto.appVersion(),
                dto.userAgent(), dto.logs());
        return Response.status(Response.Status.CREATED).entity(new FeedbackCreatedDto(id)).build();
    }

    /**
     * An error the web app did not expect. The same message from the same place in the code is
     * the same error, whichever screen it came from: it is counted, not listed again.
     */
    @POST
    @Path("/errors")
    public void error(@Valid ClientErrorDto dto) {
        quota.enforce("errors", currentUser.require().id, errorsPerHour, "Trop d'erreurs signalées en peu de temps.");
        String where = dto.stack() == null ? "" : dto.stack().strip().lines().findFirst().orElse("");
        feedback.recordError(Source.WEB, currentUser.require().id, dto.message().strip() + '|' + where,
                dto.message().strip(), dto.stack(), dto.page(), dto.appVersion(), dto.userAgent());
    }
}
