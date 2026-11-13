package zelytra.librarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * A cover thumbnail kept by the cover proxy, or the memory that there is none.
 *
 * <p>{@link #content} is {@code null} for a miss: without that row, a record the BnF holds no
 * cover for would be fetched again on every display.
 */
@Entity
@Table(name = "cover_cache")
public class CoverCache {

    @Id
    @Column(name = "cache_key", length = 64)
    public String cacheKey;

    @Column
    public byte[] content;

    @Column(name = "fetched_at", nullable = false)
    public OffsetDateTime fetchedAt;
}
