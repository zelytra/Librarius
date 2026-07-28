package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.RankCategory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class RankCategoryRepository implements PanacheRepositoryBase<RankCategory, UUID> {

    /** Categories visible to the user: built-ins (user_id NULL) plus their own. */
    public List<RankCategory> listForUser(String userId) {
        return list("userId is null or userId = ?1 order by builtin desc, sortOrder asc, label asc",
                userId);
    }

    public Optional<RankCategory> findForUser(String userId, UUID id) {
        return find("id = ?1 and (userId is null or userId = ?2)", id, userId).firstResultOptional();
    }

    /**
     * The categories the user created, and only those: the built-ins carry no
     * {@code user_id} and belong to nobody, so an export must not hand them back as the
     * user's data and an import must not try to recreate them.
     */
    public List<RankCategory> listCustomForUser(String userId) {
        return list("userId = ?1 order by code asc", userId);
    }

    /**
     * Resolves a rank by the code an export carries: the user's own category first, then the
     * shared built-in of that code.
     */
    public Optional<RankCategory> findForUserByCode(String userId, String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return find("code = ?1 and (userId = ?2 or userId is null) order by userId desc nulls last",
                code.trim(), userId).firstResultOptional();
    }

    /**
     * Whether a code is already taken among the categories the user can see — their own and
     * the built-ins. The built-ins are part of the check because they share the code space:
     * a custom category coded {@code or} would answer to the same {@code ?rank=or} filter as
     * the built-in one, and the two shelves would be impossible to tell apart.
     *
     * @param excluding identifier left out of the check, {@code null} on a creation, so
     *                  renaming a category to the label it already carries is not a
     *                  conflict with itself
     */
    public boolean codeTaken(String userId, String code, UUID excluding) {
        if (excluding == null) {
            return count("code = ?1 and (userId is null or userId = ?2)", code, userId) > 0;
        }
        return count("code = ?1 and (userId is null or userId = ?2) and id <> ?3",
                code, userId, excluding) > 0;
    }
}
