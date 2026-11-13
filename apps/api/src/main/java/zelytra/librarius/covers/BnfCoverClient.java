package zelytra.librarius.covers;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestResponse;

/**
 * REST client for the BnF cover service.
 *
 * <p>A client of its own: the cover service lives at {@code /couverture}, beside the
 * {@code /api} the catalogue client is rooted at. It answers the full-size JPEG (about
 * 500 kB) and offers no size option, hence the resizing in {@link CoverService}. A record
 * without a cover answers HTTP 500 with HTML, so the status and content type are returned
 * to the caller rather than raised as exceptions.
 */
@RegisterRestClient(configKey = "bnf-cover")
public interface BnfCoverClient {

    @GET
    @Path("/couverture")
    Uni<RestResponse<byte[]>> cover(@QueryParam("appName") String appName,
            @QueryParam("idArk") String idArk,
            @QueryParam("couverture") int couverture);
}
