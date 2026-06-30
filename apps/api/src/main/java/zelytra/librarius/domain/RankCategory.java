package zelytra.librarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Catégorie de classement : Or/Argent/Bronze (built-in) ou catégorie custom. */
@Entity
@Table(name = "rank_category")
public class RankCategory {

    @Id
    @GeneratedValue
    public UUID id;

    /** {@code null} pour les catégories prédéfinies partagées. */
    @Column(name = "user_id", length = 255)
    public String userId;

    @Column(nullable = false, length = 32)
    public String code;

    @Column(nullable = false, length = 64)
    public String label;

    @Column(length = 16)
    public String color;

    @Column(name = "sort_order", nullable = false)
    public int sortOrder;

    @Column(name = "is_builtin", nullable = false)
    public boolean builtin;
}
