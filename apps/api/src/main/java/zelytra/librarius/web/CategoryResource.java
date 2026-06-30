package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import zelytra.librarius.domain.RankCategory;
import zelytra.librarius.domain.repository.RankCategoryRepository;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.CategoryCreateDto;
import zelytra.librarius.web.ApiDtos.CategoryDto;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Catégories de classement : built-ins (Or/Argent/Bronze) + catégories custom. */
@Path("/api/categories")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class CategoryResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    RankCategoryRepository categories;

    @GET
    public List<CategoryDto> list() {
        return categories.listForUser(currentUser.id()).stream().map(CategoryDto::of).toList();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public CategoryDto create(@Valid CategoryCreateDto dto) {
        String userId = currentUser.id();
        currentUser.require();
        RankCategory cat = new RankCategory();
        cat.userId = userId;
        cat.label = dto.label().trim();
        cat.code = slug(dto.label());
        cat.color = dto.color() != null ? dto.color() : "#9a8fa6";
        cat.sortOrder = 100;
        cat.builtin = false;
        categories.persist(cat);
        return CategoryDto.of(cat);
    }

    private static String slug(String label) {
        String n = Normalizer.normalize(label, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.FRENCH)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return n.isBlank() ? "cat-" + Math.abs(label.hashCode()) : n;
    }
}
