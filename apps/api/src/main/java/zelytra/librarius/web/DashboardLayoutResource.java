package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import zelytra.librarius.dashboard.DashboardLayoutService;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.DashboardLayoutDto;

/**
 * The caller's own Home screen layout (#54): which sections show, and in which order.
 *
 * <p>No identifier in the path — like {@code /api/me}, this resource can only ever be
 * pointed at the caller's own data, so there is no cross-user case to answer 404 on.
 */
@Path("/api/dashboard/layout")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class DashboardLayoutResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    DashboardLayoutService layouts;

    @GET
    public DashboardLayoutDto get() {
        return layouts.get(currentUser.id());
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public DashboardLayoutDto save(@Valid DashboardLayoutDto dto) {
        // The row is created lazily: a fresh account provisions app_user on the first
        // write, exactly like GoalResource.upsert and CategoryResource.create do, so the
        // insert below never races the foreign key it depends on.
        currentUser.require();
        return layouts.save(currentUser.id(), dto);
    }
}
