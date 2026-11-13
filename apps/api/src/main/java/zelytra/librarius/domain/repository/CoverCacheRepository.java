package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.CoverCache;

@ApplicationScoped
public class CoverCacheRepository implements PanacheRepositoryBase<CoverCache, String> {
}
