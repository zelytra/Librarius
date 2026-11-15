package zelytra.librarius.web;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import zelytra.librarius.domain.Feedback.Source;
import zelytra.librarius.feedback.FeedbackService;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Map;

/**
 * An error no other mapper expected — a bug — answers a plain 500 and is recorded for the
 * admins (#490), with the request and the stack trace, once per place in the code: it comes
 * back as a count, not as a new line.
 *
 * <p>An answer the API meant ({@code WebApplicationException}: a 404, a 400…) keeps its own
 * response and is not recorded; the more specific mappers — validation, security, imports —
 * take their exceptions before this one.
 */
public class ServerErrorMapper {

    @Inject
    FeedbackService feedback;

    @Inject
    JsonWebToken jwt;

    @ServerExceptionMapper
    public Response unexpected(Throwable error, UriInfo uri, Request request, ResourceInfo resource) {
        if (error instanceof WebApplicationException intended) {
            return intended.getResponse();
        }
        Log.errorf(error, "Unexpected error on %s %s", request.getMethod(), uri.getPath());
        try {
            String endpoint = resource.getResourceClass() == null ? uri.getPath()
                    : resource.getResourceClass().getName() + '#' + resource.getResourceMethod().getName();
            String message = error.getClass().getSimpleName() + ": " + error.getMessage();
            feedback.recordError(Source.API, caller(), error.getClass().getName() + '|' + endpoint + '|' + origin(error),
                    message, stackTrace(error), request.getMethod() + ' ' + uri.getPath(), null, null);
        } catch (RuntimeException recording) {
            // The report is a bonus: failing to write it must not change the answer.
            Log.warn("Could not record the server error", recording);
        }
        return Response.serverError().type(MediaType.APPLICATION_JSON)
                .entity(Map.of("message", "Erreur interne du serveur.")).build();
    }

    private String caller() {
        try {
            return jwt.getSubject();
        } catch (RuntimeException anonymous) {
            return null;
        }
    }

    /** The first frame of our own code the error went through: where the bug is. */
    private static String origin(Throwable error) {
        return Arrays.stream(error.getStackTrace())
                .filter(frame -> frame.getClassName().startsWith("zelytra."))
                .findFirst()
                .map(StackTraceElement::toString)
                .orElse("");
    }

    private static String stackTrace(Throwable error) {
        StringWriter out = new StringWriter();
        error.printStackTrace(new PrintWriter(out));
        return out.toString();
    }
}
