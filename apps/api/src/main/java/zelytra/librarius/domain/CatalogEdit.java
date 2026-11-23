package zelytra.librarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A member's proposed change to a shared catalog record (#618), see V50.
 *
 * <p>{@link #changes} is the diff, as JSON: {@code {"field": {"before": "...", "after": "..."}}},
 * every value a string (or null) in the form {@code contribution.CatalogFields} parses. Read
 * and written through {@code contribution.CatalogEditService}, never by a client.
 */
@Entity
@Table(name = "catalog_edit")
public class CatalogEdit {

    @Id
    @GeneratedValue
    public UUID id;

    /** Who proposed it; NULL once their account is erased. */
    @Column(name = "submitter_id", length = 255)
    public String submitterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 16)
    public CatalogEditTarget targetType;

    @Column(name = "target_id", nullable = false)
    public UUID targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    public String changes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public CatalogEditStatus status = CatalogEditStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public VerificationOutcome verification = VerificationOutcome.NOT_RUN;

    /** Why the check failed ({@code contribution.StorePageCheck.Outcome} or {@code FETCH_FAILED}). */
    @Column(name = "verification_detail", length = 32)
    public String verificationDetail;

    /** The store page the check read — the one that confirmed the book, or the last one tried. */
    @Column(name = "verification_url", length = 1024)
    public String verificationUrl;

    @Column(name = "reviewer_id", length = 255)
    public String reviewerId;

    @Column(name = "review_note", columnDefinition = "text")
    public String reviewNote;

    @Column(name = "created_at", insertable = false, updatable = false)
    public OffsetDateTime createdAt;

    @Column(name = "reviewed_at")
    public OffsetDateTime reviewedAt;
}
