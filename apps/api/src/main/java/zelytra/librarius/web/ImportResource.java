package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import zelytra.librarius.imports.ImportService;
import zelytra.librarius.imports.ImportService.ImportResult;
import zelytra.librarius.security.CurrentUser;

/** Import de la bibliothèque depuis une source externe (scraping) ou un fichier. */
@Path("/api/import")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class ImportResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    ImportService importService;

    public record ScrapeRequest(@NotBlank String handle) {
    }

    @POST
    @Path("/{source}")
    @Consumes(MediaType.APPLICATION_JSON)
    public ImportResult scrape(@PathParam("source") String source, ScrapeRequest req) {
        currentUser.require();
        return importService.importFromSource(currentUser.id(), source, req.handle());
    }

    @POST
    @Path("/csv")
    @Consumes(MediaType.TEXT_PLAIN)
    public ImportResult csv(String body) {
        currentUser.require();
        return importService.importFromCsv(currentUser.id(), body);
    }
}
