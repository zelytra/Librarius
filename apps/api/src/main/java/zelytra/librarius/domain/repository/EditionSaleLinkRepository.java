package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.EditionSaleLink;

import java.util.List;
import java.util.UUID;

/** Sale links of an edition (#619) — catalog data, readable by every member. */
@ApplicationScoped
public class EditionSaleLinkRepository implements PanacheRepositoryBase<EditionSaleLink, UUID> {

    public List<EditionSaleLink> ofEdition(UUID editionId) {
        return list("editionId = ?1 order by store", editionId);
    }
}
