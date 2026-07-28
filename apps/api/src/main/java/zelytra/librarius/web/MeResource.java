package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.MeDto;

/** Profile of the authenticated user (provisioned on the fly when needed). */
@Path("/api/me")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class MeResource {

    @Inject
    CurrentUser currentUser;

    @GET
    public MeDto me() {
        return MeDto.of(currentUser.require());
    }
}
