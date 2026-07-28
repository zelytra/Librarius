package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.Edition;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class EditionRepository implements PanacheRepositoryBase<Edition, UUID> {

    /**
     * Every edition of a work, in the order they entered the catalog.
     *
     * <p>Ordered on the creation date rather than on the release date: most editions carry
     * no release date, and falling back to the identifier would shuffle the list on every
     * call — a section whose rows move between two renders is unusable to pick from.
     */
    public List<Edition> listByWork(UUID workId) {
        return list("work.id = ?1 order by createdAt asc, id asc", workId);
    }
}
