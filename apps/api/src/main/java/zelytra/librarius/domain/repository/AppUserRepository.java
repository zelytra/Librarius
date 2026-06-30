package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.AppUser;

@ApplicationScoped
public class AppUserRepository implements PanacheRepositoryBase<AppUser, String> {
}
