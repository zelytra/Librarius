package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import zelytra.librarius.imports.ArchiveImportService;
import zelytra.librarius.imports.ImportJobs;
import zelytra.librarius.imports.ImportService;
import zelytra.librarius.imports.ImportService.ImportResult;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.ExportDto;
import zelytra.librarius.web.ApiDtos.ImportJobDto;

import java.util.UUID;

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
    ImportJobs importJobs;

    @Inject
    ArchiveImportService archiveImportService;

    public record ScrapeRequest(@NotBlank String handle) {
    }

    /**
     * Starts a scrape and hands back a job to poll. A heavy library spans dozens of pages and
     * thousands of rows — more than a request can hold open before the ingress cuts it — so the
     * work runs on {@link ImportJobs}' own pool rather than inside this call.
     */
    @POST
    @Path("/{source}")
    @Consumes(MediaType.APPLICATION_JSON)
    public ImportJobDto scrape(@PathParam("source") String source, ScrapeRequest req) {
        currentUser.require();
        return dto(importJobs.submit(currentUser.id(), source, req.handle(), null));
    }

    /** Starts a CSV import, deferred the same way — a Goodreads export is thousands of rows too. */
    @POST
    @Path("/csv")
    @Consumes(MediaType.TEXT_PLAIN)
    public ImportJobDto csv(String body) {
        currentUser.require();
        return dto(importJobs.submit(currentUser.id(), "csv", null, body));
    }

    /** The state of a running or finished import; 404 for an id that is not the caller's. */
    @GET
    @Path("/jobs/{jobId}")
    public ImportJobDto job(@PathParam("jobId") UUID jobId) {
        currentUser.require();
        return dto(importJobs.find(currentUser.id(), jobId).orElseThrow(NotFoundException::new));
    }

    private static ImportJobDto dto(ImportJobs.Job job) {
        return new ImportJobDto(job.id, job.status().name(), job.total(), job.imported(),
                job.skipped(), job.error());
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
