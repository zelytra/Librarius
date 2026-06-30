package zelytra.librarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Souhait d'acquisition (porte priorité et prix estimé). */
@Entity
@Table(name = "wishlist_item")
public class WishlistItem {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "user_id", nullable = false, length = 255)
    public String userId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "edition_id")
    public Edition edition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public WishPriority priority = WishPriority.SOON;

    @Column(name = "estimated_price", precision = 8, scale = 2)
    public BigDecimal estimatedPrice;

    @Column(length = 512)
    public String note;

    @Column(name = "created_at", insertable = false, updatable = false)
    public OffsetDateTime createdAt;
}
