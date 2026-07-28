package zelytra.librarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * A user following a series: they want to hear about its next volumes.
 *
 * <p>The pair (user, series) is the identity — there is nothing else to carry, so no
 * surrogate key is introduced.
 */
@Entity
@Table(name = "series_follow")
@IdClass(SeriesFollow.Key.class)
public class SeriesFollow {

    @jakarta.persistence.Id
    @Column(name = "user_id", length = 255)
    public String userId;

    @jakarta.persistence.Id
    @Column(name = "series_id")
    public UUID seriesId;

    @Column(name = "created_at", insertable = false, updatable = false)
    public OffsetDateTime createdAt;

    /** Composite identifier of a follow, required by JPA for the two-column primary key. */
    public static class Key implements Serializable {

        public String userId;
        public UUID seriesId;

        public Key() {
        }

        public Key(String userId, UUID seriesId) {
            this.userId = userId;
            this.seriesId = seriesId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(userId, key.userId) && Objects.equals(seriesId, key.seriesId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, seriesId);
        }
    }
}
