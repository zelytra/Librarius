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
 * A run of works published under a common title: a manga series, or a book cycle.
 *
 * <p>Shared catalog data, like {@link Work} and {@link Edition}: what belongs to a user is
 * the ownership of the volumes ({@link LibraryItem}) and the follow ({@link SeriesFollow}).
 */
@Entity
@Table(name = "series")
public class Series {

    @Id
    @GeneratedValue
    public UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public Kind kind;

    @Column(nullable = false, length = 512)
    public String title;

    @Column(name = "original_title", length = 512)
    public String originalTitle;

    /** Number of volumes the run will have, or {@code null} when it is unknown. */
    @Column(name = "total_volumes")
    public Integer totalVolumes;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    public SeriesStatus status;

    @Column(name = "cover_url", length = 1024)
    public String coverUrl;

    @Column(columnDefinition = "text")
    public String synopsis;

    @Column(length = 32)
    public String provider;

    @Column(name = "provider_ref", length = 255)
    public String providerRef;

    @Column(name = "created_at", insertable = false, updatable = false)
    public OffsetDateTime createdAt;
}
