package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.WishlistItem;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class WishlistItemRepository implements PanacheRepositoryBase<WishlistItem, UUID> {

    public List<WishlistItem> listByUser(String userId) {
        return list("userId = ?1 order by priority asc, createdAt desc", userId);
    }

    public boolean deleteOwned(String userId, UUID id) {
        return delete("id = ?1 and userId = ?2", id, userId) > 0;
    }
}
