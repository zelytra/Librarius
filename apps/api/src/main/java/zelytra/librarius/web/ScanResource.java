package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import zelytra.librarius.catalog.RateLimiter;
import zelytra.librarius.scan.Isbn;
import zelytra.librarius.scan.ScanService;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.ScanResultDto;

/** A barcode scanned in a bookshop: is this book mine, wished for, or new to me (#68)? */
@Path("/api/scan")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class ScanResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    ScanService scans;

    @Inject
    RateLimiter rateLimiter;

    /**
     * Answers for one ISBN — 13 digits, or an ISBN-10 converted. A scan answered from the
     * reader's own lists costs nothing; one that has to ask the catalogs is charged against the
     * same quota as a catalog search, so a scanner left running cannot hammer the providers.
     */
    @GET
    @Path("/{isbn}")
    public ScanResultDto scan(@PathParam("isbn") String raw) {
        currentUser.require();
        String isbn13 = Isbn.toIsbn13(raw);
        if (isbn13 == null) {
            throw new BadRequestException(Response.status(400)
                    .entity("{\"message\":\"Ce code n'est pas un ISBN.\"}")
                    .type(MediaType.APPLICATION_JSON).build());
        }
        return scans.lookUp(currentUser.id(), isbn13, this::enforceQuota);
    }

    private void enforceQuota() {
        RateLimiter.Decision decision = rateLimiter.check(currentUser.id());
        if (!decision.allowed()) {
            throw new ClientErrorException(Response.status(429)
                    .header("Retry-After", decision.retryAfterSeconds())
                    .entity("{\"message\":\"Trop de recherches. Réessayez dans un instant.\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build());
        }
    }
}
