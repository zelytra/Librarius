package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.CatalogEdit;
import zelytra.librarius.domain.CatalogEditStatus;
import zelytra.librarius.domain.CatalogEditTarget;

import java.util.UUID;

/**
 * Catalog edit proposals (#618). Not user-scoped by design: the admin list reads them all, and
 * a member only ever reaches their own through {@link #hasPending}.
 */
@ApplicationScoped
public class CatalogEditRepository implements PanacheRepositoryBase<CatalogEdit, UUID> {

    public boolean hasPending(String submitterId, CatalogEditTarget type, UUID targetId) {
        return count("submitterId = ?1 and targetType = ?2 and targetId = ?3 and status = ?4",
                submitterId, type, targetId, CatalogEditStatus.PENDING) > 0;
    }
}
