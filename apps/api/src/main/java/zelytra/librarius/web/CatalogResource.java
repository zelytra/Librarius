package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import zelytra.librarius.catalog.CatalogResult;
import zelytra.librarius.catalog.CatalogService;
import zelytra.librarius.domain.Kind;

import java.util.List;

/** Recherche dans le catalogue externe et prochaines sorties. */
@Path("/api/catalog")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class CatalogResource {

    @Inject
    CatalogService catalog;

    @GET
    @Path("/search")
    public List<CatalogResult> search(@QueryParam("q") String query,
            @QueryParam("kind") Kind kind,
            @QueryParam("limit") @DefaultValue("20") int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        Kind target = kind != null ? kind : Kind.BOOK;
        return catalog.search(target, query.trim(), Math.clamp(limit, 1, 40));
    }

    @GET
    @Path("/upcoming")
    public List<CatalogResult> upcoming(@QueryParam("kind") Kind kind,
            @QueryParam("limit") @DefaultValue("20") int limit) {
        Kind target = kind != null ? kind : Kind.MANGA;
        return catalog.upcoming(target, Math.clamp(limit, 1, 50));
    }
}
