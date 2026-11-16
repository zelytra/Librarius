package zelytra.librarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A report (#489, #490): sent by a member from the bug button, or recorded by the web app or
 * the API on an error they did not expect. Read by the admins only.
 */
@Entity
@Table(name = "feedback")
public class Feedback {

    /** What the report is about; {@code ERROR} is for the ones recorded by themselves. */
    public enum Kind { BUG, IDEA, OTHER, ERROR }

    /** Who wrote it: a member, the web app, or the API. */
    public enum Source { USER, WEB, API }

    /** Where an admin stands with it (#495): not opened yet, opened, dealt with, or set aside. */
    public enum Status { NEW, SEEN, HANDLED, IRRELEVANT }

    @Id
    public UUID id;

    /** The reporter, or null once their account is erased (or for an error without a caller). */
    @Column(name = "user_id", length = 255)
    public String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    public Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    public Source source;

    @Column(nullable = false, columnDefinition = "text")
    public String message;

    /** The logs a member attached, or an error's stack trace. */
    @Column(columnDefinition = "text")
    public String details;

    /** The screen, or for the API the method and path. */
    @Column(length = 1024)
    public String page;

    @Column(name = "app_version", length = 64)
    public String appVersion;

    @Column(name = "user_agent", length = 512)
    public String userAgent;

    /** An error's identity, so that it is counted rather than listed again. */
    @Column(length = 64)
    public String fingerprint;

    @Column(nullable = false)
    public int occurrences = 1;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;

    @Column(name = "last_seen_at", nullable = false)
    public OffsetDateTime lastSeenAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    public Status status = Status.NEW;

    /** When an admin last changed {@link #status}; null while it is untouched. */
    @Column(name = "status_changed_at")
    public OffsetDateTime statusChangedAt;
}
