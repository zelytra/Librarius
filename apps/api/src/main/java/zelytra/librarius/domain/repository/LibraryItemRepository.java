package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.LibraryStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class LibraryItemRepository implements PanacheRepositoryBase<LibraryItem, UUID> {

    public List<LibraryItem> listByUser(String userId) {
        return list("userId = ?1 order by createdAt desc", userId);
    }

    public List<LibraryItem> listByUserAndStatus(String userId, LibraryStatus status) {
        return list("userId = ?1 and status = ?2 order by createdAt desc", userId, status);
    }

    /** Recherche scoping-safe : ne renvoie l'item que s'il appartient à l'utilisateur. */
    public Optional<LibraryItem> findOwned(String userId, UUID id) {
        return find("id = ?1 and userId = ?2", id, userId).firstResultOptional();
    }

    public boolean deleteOwned(String userId, UUID id) {
        return delete("id = ?1 and userId = ?2", id, userId) > 0;
    }
}
