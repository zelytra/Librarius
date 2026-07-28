package zelytra.librarius.web;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import zelytra.librarius.imports.ImportException;

import java.util.Map;

/** Returns a readable import message (400) instead of a 500. */
@Provider
public class ImportExceptionMapper implements ExceptionMapper<ImportException> {
    @Override
    public Response toResponse(ImportException e) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("message", e.getMessage()))
                .build();
    }
}
