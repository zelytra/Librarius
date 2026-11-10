package zelytra.librarius.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import zelytra.librarius.domain.AppUser;

@ApplicationScoped
public class AppUserRepository implements PanacheRepositoryBase<AppUser, String> {

    /**
     * Members whose display name holds {@code needle}, case aside — the caller left out, and
     * anyone a block stands between them and the caller, in either direction.
     */
    public java.util.List<AppUser> searchByName(String callerId, String needle, int limit) {
        String pattern = "%" + needle.trim().toLowerCase(java.util.Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return getEntityManager().createQuery("""
                        select u from AppUser u
                        where u.id <> :caller
                          and lower(coalesce(u.displayName, '')) like :pattern escape '!'
                          and not exists (select 1 from UserBlock b
                                          where (b.blockerId = :caller and b.blockedId = u.id)
                                             or (b.blockerId = u.id and b.blockedId = :caller))
                        order by lower(u.displayName) asc, u.id asc""", AppUser.class)
                .setParameter("caller", callerId)
                .setParameter("pattern", pattern)
                .setMaxResults(limit)
                .getResultList();
    }
}
