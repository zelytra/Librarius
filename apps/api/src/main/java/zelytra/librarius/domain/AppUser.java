package zelytra.librarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Utilisateur applicatif. L'identifiant est le « sub » du jeton Keycloak ;
 * aucun mot de passe n'est stocké (auth déléguée à Keycloak).
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

    @Column(name = "created_at", insertable = false, updatable = false)
    public OffsetDateTime createdAt;
}
