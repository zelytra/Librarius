package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.AppUser;

import java.util.List;

@ApplicationScoped
public class AppUserRepository implements PanacheRepositoryBase<AppUser, String> {

    /**
     * The accounts not trusted yet. The trust evaluation only ever promotes — an account that
     * already earned the flag is left alone, revocation being #195 — so a run looks at these
     * alone rather than re-deciding for everyone.
     */
    public List<AppUser> listUntrusted() {
        return list("trusted = false");
    }
}
