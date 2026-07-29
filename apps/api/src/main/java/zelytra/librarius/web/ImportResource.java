package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import zelytra.librarius.imports.ArchiveImportService;
import zelytra.librarius.imports.ImportService;
import zelytra.librarius.imports.ImportService.ImportResult;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.ExportDto;

/** Library import from an external source (scraping) or from a file. */
@Path("/api/import")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class ImportResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    ImportService importService;

    @Inject
    ArchiveImportService archiveImportService;

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

    /**
     * Restores a document produced by {@code GET /api/export?format=json}.
     *
     * <p>The other half of the portability promise: without it the export is a file the user
     * can look at, not a library they can move. Additive — a title already present is
     * skipped, never duplicated, and nothing is ever deleted.
     */
    @POST
    @Path("/json")
    @Consumes(MediaType.APPLICATION_JSON)
    public ImportResult json(@Valid ExportDto document) {
        currentUser.require();
        return archiveImportService.restore(currentUser.id(), document);
    }
}
