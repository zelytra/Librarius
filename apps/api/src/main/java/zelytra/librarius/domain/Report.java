package zelytra.librarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A member's flag that a shared catalog object carries an error (#192).
 *
 * <p>Write-only from the client's point of view: it is created and never read back through the
 * API. What consumes it is the automatic trust revocation (#195), and — later — an admin view;
 * neither exists yet. {@code targetType} + {@code targetId} name the flagged object, with no
 * foreign key spanning the three possible tables, so {@code ReportService} resolves the target
 * before a row is written.
 */
@Entity
@Table(name = "report")
public class Report {

    @Id
    @GeneratedValue
    public UUID id;

    /** The author of the report — {@code CurrentUser.id()}. Never exposed to anyone else. */
    @Column(name = "reporter_id", nullable = false, length = 255)
    public String reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 16)
    public ReportTargetType targetType;

    /** Identifier of the flagged {@code work}, {@code edition} or {@code series}. */
    @Column(name = "target_id", nullable = false)
    public UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    public ReportReason reason;

    /** Optional free text with the specifics the reason picklist cannot carry. */
    @Column(columnDefinition = "text")
    public String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public ReportStatus status = ReportStatus.OPEN;

    @Column(name = "created_at", insertable = false, updatable = false)
    public OffsetDateTime createdAt;
}
