package zelytra.librarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import zelytra.librarius.contribution.SaleStore;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Where an edition can be bought (#619): one link per store, written only by a reviewed edit. */
@Entity
@Table(name = "edition_sale_link")
public class EditionSaleLink {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "edition_id", nullable = false)
    public UUID editionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    public SaleStore store;

    @Column(nullable = false, length = 1024)
    public String url;

    @Column(name = "created_at", insertable = false, updatable = false)
    public OffsetDateTime createdAt;
}
