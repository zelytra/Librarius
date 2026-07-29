package zelytra.librarius.web;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import zelytra.librarius.account.AccountDeletionUnavailableException;

import java.util.Map;

/**
 * Turns a refused account deletion into a 503 carrying the reason, in the same
 * {@code {"message": …}} shape as the import errors — the front end already knows how to
 * read it.
 *
 * <p>503 rather than 500: the request was valid and nothing is broken about it. The service
 * cannot honour it right now, and trying again later is exactly the right advice.
 */
@Provider
public class AccountDeletionExceptionMapper
        implements ExceptionMapper<AccountDeletionUnavailableException> {

    @Override
    public Response toResponse(AccountDeletionUnavailableException e) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(Map.of("message", e.getMessage()))
                .build();
    }
}
