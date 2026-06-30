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
 * Identité de l'appelant, dérivée du jeton OIDC. Provisionne l'{@link AppUser}
 * à la volée (JIT) lors du premier accès authentifié. Centralise l'accès à
 * l'identifiant : toutes les requêtes métier doivent passer par ici pour le scoping.
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
     * Identifiant stable de l'utilisateur courant : le « sub » du jeton lorsqu'il
     * est présent, sinon le nom du principal (préservé unique au sein du realm).
     */
    public String id() {
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            sub = identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
        }
        if (sub == null || sub.isBlank()) {
            throw new ForbiddenException("Jeton sans identifiant d'utilisateur exploitable.");
        }
        return sub;
    }

    /** Renvoie l'utilisateur applicatif, en le créant s'il n'existe pas encore. */
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
