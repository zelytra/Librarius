package zelytra.librarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Application user. The identifier is the Keycloak token "sub";
 * no password is stored (authentication is delegated to Keycloak).
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    public String id;

    @Column(length = 255)
    public String email;

    @Column(name = "display_name", length = 255)
    public String displayName;

    @Column(length = 16)
    public String locale = "fr";

    /**
     * IANA time-zone identifier the greeting and the date formatting follow, e.g.
     * {@code Europe/Paris}. Nullable: an account that never set one keeps the client's own
     * zone (V14, #75).
     */
    @Column(name = "time_zone", length = 64)
    public String timeZone;

    @Column(name = "created_at", insertable = false, updatable = false)
    public OffsetDateTime createdAt;

    /**
     * Server-computed trust flag (V16, #180): the account's catalog contributions can be
     * trusted. Never set by a user — neither on themselves nor on anyone else. The only writer
     * is {@link zelytra.librarius.trust.TrustEvaluator}, off the request path, and no endpoint
     * accepts it as input. Defaults to {@code false}.
     */
    @Column(nullable = false)
    public boolean trusted = false;

    /** When {@link #trusted} was first earned; {@code null} while the account is not trusted. */
    @Column(name = "trusted_at")
    public OffsetDateTime trustedAt;

    /**
     * Whether the account waived the default private visibility (V20, #201). When {@code true}
     * the account's shared content is readable by any signed-in member; when {@code false} —
     * the default — it is readable only through a mutual follow. The account's own choice, set
     * through {@code PATCH /api/me}, and the sole per-account input to
     * {@link zelytra.librarius.social.VisibilityGate}. Defaults to {@code false}.
     */
    @Column(name = "public_account", nullable = false)
    public boolean publicAccount = false;
}
