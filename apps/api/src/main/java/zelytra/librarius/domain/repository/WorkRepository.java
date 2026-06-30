package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.Work;

import java.util.UUID;

@ApplicationScoped
public class WorkRepository implements PanacheRepositoryBase<Work, UUID> {
}
