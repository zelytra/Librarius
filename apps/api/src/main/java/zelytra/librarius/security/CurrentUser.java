package zelytra.librarius.security;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import org.eclipse.microprofile.jwt.JsonWebToken;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.domain.repository.AppUserRepository;

/**
 * Caller identity, derived from the OIDC token. Provisions the {@link AppUser}
 * just in time (JIT) on the first authenticated access. Centralizes access to the
 * identifier: every business query must go through here for scoping.
 */
@RequestScoped
public class CurrentUser {

    @Inject
    SecurityIdentity identity;

    @Inject
    JsonWebToken jwt;

    @Inject
    AppUserRepository users;

    /**
     * Stable identifier of the current user: the token "sub" when present,
     * otherwise the principal name (guaranteed unique within the realm).
     */
    public String id() {
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            sub = identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
        }
        if (sub == null || sub.isBlank()) {
            throw new ForbiddenException("Token without a usable user identifier.");
        }
        return sub;
    }

    /** Returns the application user, creating it when it does not exist yet. */
    @Transactional
    public AppUser require() {
        String id = id();
        AppUser user = users.findById(id);
        if (user == null) {
            user = new AppUser();
            user.id = id;
            user.email = claim("email");
            user.displayName = firstNonBlank(claim("name"), claim("preferred_username"),
                    principalName(), "Lecteur");
            user.locale = firstNonBlank(claim("locale"), "fr");
            users.persist(user);
        }
        return user;
    }

    private String principalName() {
        return identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
    }

    private String claim(String name) {
        Object value = jwt.getClaim(name);
        return value == null ? null : value.toString();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
