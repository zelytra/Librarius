package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.RankCategory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class RankCategoryRepository implements PanacheRepositoryBase<RankCategory, UUID> {

    /** Catégories visibles par l'utilisateur : built-ins (user_id NULL) + les siennes. */
    public List<RankCategory> listForUser(String userId) {
        return list("userId is null or userId = ?1 order by builtin desc, sortOrder asc, label asc",
                userId);
    }

    public Optional<RankCategory> findForUser(String userId, UUID id) {
        return find("id = ?1 and (userId is null or userId = ?2)", id, userId).firstResultOptional();
    }
}
