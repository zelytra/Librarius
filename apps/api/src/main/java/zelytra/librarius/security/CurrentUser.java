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

    /**
     * The Keycloak realm role of the people who run the instance (#488), granted by hand in the
     * Keycloak console; Quarkus reads it from the token's {@code realm_access.roles}.
     */
    public static final String ADMIN = "admin";

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

    /** Whether the caller holds the {@link #ADMIN} role. */
    public boolean isAdmin() {
        return identity.hasRole(ADMIN);
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
            user.displayName = initialDisplayName(claim("name"), claim("preferred_username"));
            user.locale = firstNonBlank(claim("locale"), "fr");
            user.username = firstNonBlank(claim("preferred_username"), principalName());
            user.admin = isAdmin();
            users.persist(user);
        } else {
            // Kept in step with Keycloak for the admins' list (#499); a no-op once it matches.
            String username = firstNonBlank(claim("preferred_username"), principalName());
            if (username != null && !username.equals(user.username)) {
                user.username = username;
            }
            // The role lives in Keycloak; mirrored so another member's profile can show it (#625).
            if (user.admin != isAdmin()) {
                user.admin = isAdmin();
            }
        }
        return user;
    }

    /**
     * The display name a new account starts with: the token's name, else its username (never the opaque subject) — but
     * never a value that looks like an e-mail address (#538), since other members see it. An
     * account created through Google or Apple has no name and an e-mail for a username, so it
     * starts with the neutral default and the reader sets their own in Settings.
     */
    static String initialDisplayName(String name, String preferredUsername) {
        return firstNonBlank(notAnEmail(name), notAnEmail(preferredUsername), DEFAULT_DISPLAY_NAME);
    }

    private static final String DEFAULT_DISPLAY_NAME = "Lecteur";

    private static String notAnEmail(String value) {
        return value != null && value.contains("@") ? null : value;
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
