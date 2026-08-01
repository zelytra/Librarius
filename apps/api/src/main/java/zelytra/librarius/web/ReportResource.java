package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import zelytra.librarius.domain.Report;
import zelytra.librarius.report.ReportService;
import zelytra.librarius.report.UnknownReportTargetException;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.ReportCreateDto;
import zelytra.librarius.web.ApiDtos.ReportDto;

/**
 * Flagging an error in a shared catalog object (#192).
 *
 * <p>Open to every authenticated member, and write-only: a report is a private signal from a
 * user to the application, consumed by the automatic revocation (#195), and no endpoint reads
 * one back. The reporter is always {@code CurrentUser.id()} and never taken from the body, so
 * one member can neither file a report as another nor discover a report another filed — there
 * is nothing to discover through this resource.
 */
@Path("/api/reports")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class ReportResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    ReportService reports;

    /**
     * Files a report. Answers 201 with the created report, or 400 when the target does not
     * exist — reporting an unknown object must fail rather than succeed silently.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@Valid ReportCreateDto dto) {
        currentUser.require();
        try {
            Report report = reports.create(currentUser.id(), dto.targetType(), dto.targetId(),
                    dto.reason(), dto.comment());
            return Response.status(Response.Status.CREATED).entity(ReportDto.of(report)).build();
        } catch (UnknownReportTargetException unknown) {
            throw new BadRequestException(unknown.getMessage());
        }
    }
}
