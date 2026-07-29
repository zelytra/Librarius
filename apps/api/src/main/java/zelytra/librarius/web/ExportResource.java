package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import zelytra.librarius.export.ExportFormat;
import zelytra.librarius.export.ExportJobs;
import zelytra.librarius.export.ExportJobs.Job;
import zelytra.librarius.export.ExportService;
import zelytra.librarius.export.ExportService.ExportFile;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.ExportJobDto;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Everything the caller entered, handed back to them (GDPR art. 20).
 *
 * <p>Two shapes, one endpoint: {@code json} is the complete archive and the only one
 * {@code POST /api/import/json} takes back, {@code csv} is the book list in the vocabulary
 * the other reading trackers use.
 */
@Path("/api/export")
@Authenticated
public class ExportResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    ExportService exports;

    @Inject
    ExportJobs jobs;

    /**
     * The caller's account.
     *
     * <p>Answers with the file itself while the account fits in a request, and with a
     * {@code 202} plus a job to poll past that — see {@link ExportJobs}.
     *
     * @param format {@code json} (default) or {@code csv}; anything else is a 400
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, "text/csv" })
    public Response export(@QueryParam("format") String format) {
        ExportFormat wanted = ExportFormat.parse(format)
                .orElseThrow(() -> new BadRequestException("Unknown export format: " + format));
        String userId = currentUser.require().id;

        int rows = exports.rowCount(userId);
        if (jobs.fitsInRequest(rows)) {
            return file(exports.build(userId, wanted));
        }
        Job job = jobs.submit(userId, wanted, rows);
        return Response.status(Response.Status.ACCEPTED)
                .entity(dto(job))
                .type(MediaType.APPLICATION_JSON)
                .header("Location", "/api/export/" + job.id)
                .build();
    }

    /**
     * A deferred export: the file once it is ready, {@code 202} while it is not.
     *
     * <p>A job belonging to somebody else answers 404, exactly like one that never existed —
     * a 403 would confirm that another user is exporting, and the identifier is the only
     * thing standing between a caller and a whole library.
     */
    @GET
    @Path("/{jobId}")
    @Produces({ MediaType.APPLICATION_JSON, "text/csv" })
    public Response job(@PathParam("jobId") UUID jobId) {
        Job job = jobs.find(currentUser.id(), jobId).orElseThrow(NotFoundException::new);
        return switch (job.status()) {
            case READY -> file(job.file());
            case PENDING -> Response.status(Response.Status.ACCEPTED).entity(dto(job))
                    .type(MediaType.APPLICATION_JSON).build();
            case FAILED -> Response.serverError().entity(dto(job))
                    .type(MediaType.APPLICATION_JSON).build();
        };
    }

    /** Sends the bytes as an attachment, so a browser saves rather than renders them. */
    private static Response file(ExportFile file) {
        return Response.ok(file.content(), file.format().contentType)
                .header("Content-Disposition", "attachment; filename=\"" + file.filename() + "\"")
                .build();
    }

    private static ExportJobDto dto(Job job) {
        return new ExportJobDto(job.id, job.status().name(), job.format.extension, job.rows,
                OffsetDateTime.ofInstant(job.createdAt, ZoneOffset.UTC));
    }
}
