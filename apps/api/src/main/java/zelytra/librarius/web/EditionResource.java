package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import zelytra.librarius.contribution.CatalogEditService;
import zelytra.librarius.web.ApiDtos.SaleLinkDto;

import java.util.List;
import java.util.UUID;

/** Shared facts about one edition. Catalog data: the same for every member. */
@Path("/api/editions")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class EditionResource {

    @Inject
    CatalogEditService edits;

    /** Where the edition can be bought (#619), by store. 404 for an unknown edition. */
    @GET
    @Path("/{id}/sale-links")
    public List<SaleLinkDto> saleLinks(@PathParam("id") UUID id) {
        return edits.saleLinksOf(id);
    }
}
