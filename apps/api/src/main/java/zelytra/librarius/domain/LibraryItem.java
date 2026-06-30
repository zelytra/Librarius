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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Possession d'une édition par un utilisateur. */
@Entity
@Table(name = "library_item")
public class LibraryItem {

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
    public LibraryStatus status = LibraryStatus.OWNED;

    public Integer rating;

    @Column(name = "acquired_at")
    public LocalDate acquiredAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    public OffsetDateTime createdAt;
}
