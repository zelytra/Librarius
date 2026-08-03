package zelytra.librarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A member's own rating and review of a series as a whole (#190) — the series-level
 * counterpart of the per-title {@code library_item.rating}/{@code review} (#48).
 *
 * <p>Strictly private, exactly like the title review: stored on the caller's own row,
 * returned to nobody else, and never folded into anything the title review reports on.
 * Making a review visible to other members (#205) and rolling reviews up into a public
 * score (#206) are both out of scope here.
 */
@Entity
@Table(name = "series_review")
public class SeriesReview {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "user_id", nullable = false, length = 255)
    public String userId;

    @Column(name = "series_id", nullable = false)
    public UUID seriesId;

    @Column(nullable = false)
    public Integer rating;

    @Column(columnDefinition = "text")
    public String review;

    @Column(name = "created_at", insertable = false, updatable = false)
    public OffsetDateTime createdAt;

    @Column(name = "updated_at")
    public OffsetDateTime updatedAt;
}
