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

    /**
     * The reader's own price for their copy (#431, V30), private to this row: the shared
     * {@code edition.list_price} is never written by it. The library value counts it first.
     */
    @Column(precision = 8, scale = 2)
    public java.math.BigDecimal price;

    /**
     * The reader's own page count for their copy (#446, V32), private to this row. Every figure
     * of the reader prefers it — see {@link #effectivePageCount()}.
     */
    @Column(name = "page_count")
    public Integer pageCount;

    /** The format of the reader's copy (#447, V32), {@code null} when unsaid. */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    public BookFormat format;

    /** The length the reader's figures use: theirs, else the edition's, else its estimate. */
    public Integer effectivePageCount() {
        if (pageCount != null) {
            return pageCount;
        }
        return edition != null ? edition.effectivePageCount() : null;
    }

    /** Whether the title was ever abandoned (#287, V31): finishing it then is a comeback. */
    @Column(name = "was_abandoned", nullable = false)
    public boolean wasAbandoned;

    @Column(name = "created_at", insertable = false, updatable = false)
    public OffsetDateTime createdAt;
}
