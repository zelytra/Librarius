package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/** Test-only: an endpoint that fails the way a bug does, for the server error reporting (#490). */
@Path("/api/test/boom")
@Authenticated
public class BoomResource {

    static final String MESSAGE = "boom for the error reporting test";

    @GET
    public String boom() {
        throw new IllegalStateException(MESSAGE);
    }
}
