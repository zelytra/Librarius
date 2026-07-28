package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import zelytra.librarius.category.CategoryService;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.CategoryCreateDto;
import zelytra.librarius.web.ApiDtos.CategoryDto;
import zelytra.librarius.web.ApiDtos.CategoryUpdateDto;

import java.util.List;
import java.util.UUID;

/**
 * Ranking categories: the built-ins (Or / Argent / Bronze) plus the caller's own.
 *
 * <p>The rules — ownership, read-only built-ins, one name per user, and what deleting a
 * category does to the titles filed under it — live in {@link CategoryService}.
 */
@Path("/api/categories")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class CategoryResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    CategoryService service;

    /** The built-ins, shared by everyone, followed by the caller's own categories. */
    @GET
    public List<CategoryDto> list() {
        return service.list(currentUser.id()).stream().map(CategoryDto::of).toList();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public CategoryDto create(@Valid CategoryCreateDto dto) {
        currentUser.require();
        return CategoryDto.of(service.create(currentUser.id(), dto.label(), dto.color()));
    }

    /**
     * Renames one of the caller's categories. The titles filed under it keep their rank.
     *
     * <p>A built-in answers 403: it is shared by every account, and the caller has just
     * listed it, so denying its existence would be a lie. Someone else's category answers
     * 404, like an unknown identifier. A name the caller already uses answers 409.
     */
    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public CategoryDto update(@PathParam("id") UUID id, @Valid CategoryUpdateDto dto) {
        return CategoryDto.of(service.rename(currentUser.id(), id, dto.label()));
    }

    /**
     * Deletes one of the caller's categories. The titles that were in it stay in the
     * collection and lose their rank — see {@link CategoryService#delete}.
     */
    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") UUID id) {
        service.delete(currentUser.id(), id);
        return Response.noContent().build();
    }
}
