package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.Edition;

import java.util.UUID;

@ApplicationScoped
public class EditionRepository implements PanacheRepositoryBase<Edition, UUID> {
}
