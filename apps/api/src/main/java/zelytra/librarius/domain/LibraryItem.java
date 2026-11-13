package zelytra.librarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Ownership of an edition by a user. */
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
    public LibraryStatus status = LibraryStatus.TO_READ;

    /** Whether the reader has the book, and in what form (#411, V27). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public Ownership ownership = Ownership.OWNED;

    @ManyToOne
    @JoinColumn(name = "rank_category_id")
    public RankCategory rankCategory;

    /** Personal rating, 1 to 5. Private to the owner: never shared, never aggregated. */
    public Integer rating;

    /** Free-text notes on the title, as private as the rating above. */
    @Column(columnDefinition = "text")
    public String review;

    /** Whether the reader shares the rating and review above (#205); private by default. */
    @Column(name = "review_shared", nullable = false)
    public boolean reviewShared;

    /**
     * Where the reader stands in the book. The inverse side of the 1-1, so that a single
     * query can hand back an item and its progress; the list queries fetch it explicitly
     * rather than paying one extra select per row.
     */
    @OneToOne(mappedBy = "libraryItem")
    public ReadingProgress progress;

    @Column(name = "acquired_at")
    public LocalDate acquiredAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    public OffsetDateTime createdAt;
}
