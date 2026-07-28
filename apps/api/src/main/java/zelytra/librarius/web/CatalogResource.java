package zelytra.librarius.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import zelytra.librarius.catalog.CatalogQuery;
import zelytra.librarius.catalog.CatalogResult;
import zelytra.librarius.catalog.CatalogService;
import zelytra.librarius.catalog.RateLimiter;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.security.CurrentUser;

import java.util.List;

/** External catalog search and upcoming releases. */
@Path("/api/catalog")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class CatalogResource {

    @Inject
    CatalogService catalog;

    @Inject
    MeterRegistry meters;

    @Inject
    CurrentUser currentUser;

    @Inject
    RateLimiter rateLimiter;

    /**
     * Searches the external catalogs. {@code q} is the free text; the other criteria are the
     * advanced form, and each provider honours the ones its own API indexes — see
     * {@link zelytra.librarius.catalog.CatalogQuery}.
     */
    @GET
    @Path("/search")
    public List<CatalogResult> search(@QueryParam("q") String query,
            @QueryParam("author") String author,
            @QueryParam("year") Integer year,
            @QueryParam("language") String language,
            @QueryParam("publisher") String publisher,
            @QueryParam("isbn") String isbn,
            @QueryParam("kind") Kind kind,
            @QueryParam("limit") @DefaultValue("20") int limit) {
        CatalogQuery criteria = new CatalogQuery(query, author, year, language, publisher, isbn);
        if (criteria.isEmpty()) {
            return List.of();
        }
        // Checked after the blank guard: an empty query never reaches a provider, so
        // charging it against the quota would penalise a stray keystroke.
        enforceQuota();
        Kind target = kind != null ? kind : Kind.BOOK;
        // Business metric: number of catalog searches per kind.
        meters.counter("librarius.catalog.search", "kind", target.name()).increment();
        return catalog.search(target, criteria, Math.clamp(limit, 1, 40));
    }

    @GET
    @Path("/upcoming")
    public List<CatalogResult> upcoming(@QueryParam("kind") Kind kind,
            @QueryParam("limit") @DefaultValue("20") int limit) {
        enforceQuota();
        Kind target = kind != null ? kind : Kind.MANGA;
        return catalog.upcoming(target, Math.clamp(limit, 1, 50));
    }

    /**
     * Rejects the call with 429 and a {@code Retry-After} header once the caller has used
     * up their share of the instance's provider quota.
     */
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
