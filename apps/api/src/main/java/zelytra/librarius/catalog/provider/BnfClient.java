package zelytra.librarius.catalog.provider;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * REST client for the BnF general catalogue, over SRU (books, no API key).
 *
 * <p>SRU is a query protocol, not a JSON API: {@code version} and {@code operation} are part
 * of every request and are passed explicitly rather than hidden in configuration, so the
 * whole request is visible at the one call site that builds it.
 *
 * <p>The answer is taken as a {@code String} and parsed by {@link BnfProvider}. Binding the
 * SRU envelope to records would mean adding an XML dataformat to a build that has none, to
 * read six Dublin Core fields out of it.
 *
 * <p>Returns a {@code Uni} rather than the response itself so the caller can put an absolute
 * deadline on the call, for the same reason {@link OpenLibraryClient} does: {@code
 * read-timeout} bounds silence, not slowness.
 */
@RegisterRestClient(configKey = "bnf")
public interface BnfClient {

    @GET
    @Path("/SRU")
    @Produces(MediaType.APPLICATION_XML)
    Uni<String> search(@QueryParam("version") String version,
            @QueryParam("operation") String operation,
            @QueryParam("query") String query,
            @QueryParam("recordSchema") String recordSchema,
            @QueryParam("maximumRecords") int maximumRecords);
}
