package zelytra.librarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    /**
     * The country the member says they live in (V44, #573), ISO 3166-1 alpha-2, e.g. {@code FR}.
     * Nullable and only ever set by the member: the admins see a count per country, never who.
     */
    @Column(length = 2)
    public String country;

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

    /**
     * When the achievements were first evaluated for this account (#287, V31). Null until then:
     * that first evaluation unlocks what was already earned silently, as already seen.
     */
    @Column(name = "achievements_evaluated_at")
    public OffsetDateTime achievementsEvaluatedAt;

    /** Set while the member has a profile picture (#500, V39), changing with each one. */
    @Column(name = "avatar_version")
    public Long avatarVersion;

    /** The Keycloak username (#499, V40), shown to the admins only. */
    @Column(length = 255)
    public String username;

    /** Whether comments on this member's activity notify them (#211, V41). */
    @Column(name = "notify_comments", nullable = false)
    public boolean notifyComments = true;

    /**
     * The version of the terms of service and privacy policy the member last accepted (#74),
     * {@code null} until they do. Compared with {@code LegalTerms.CURRENT_VERSION}.
     */
    @Column(name = "terms_version")
    public Integer termsVersion;

    /** When they accepted it, on the server's clock. */
    @Column(name = "terms_accepted_at")
    public OffsetDateTime termsAcceptedAt;

    /** Display preferences carried across devices (#613); never {@code null}, empty when nothing is chosen. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    public UserPreferences preferences = UserPreferences.NONE;
}
