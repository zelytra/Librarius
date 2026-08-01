package zelytra.librarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * A user following another user (#200): the first relationship the schema draws between two
 * accounts.
 *
 * <p>The pair (follower, followee) is the identity — there is nothing else to carry, so no
 * surrogate key is introduced, the same shape {@link SeriesFollow} and {@link AuthorFollow}
 * use. Those two link a user to a catalog entity; this one links a user to another user, and
 * following is one-directional: a mutual pair is what "friends" means here.
 */
@Entity
@Table(name = "user_follow")
@IdClass(UserFollow.Key.class)
public class UserFollow {

    @jakarta.persistence.Id
    @Column(name = "follower_id", length = 255)
    public String followerId;

    @jakarta.persistence.Id
    @Column(name = "followee_id", length = 255)
    public String followeeId;

    @Column(name = "created_at", insertable = false, updatable = false)
    public OffsetDateTime createdAt;

    /** Composite identifier of a follow, required by JPA for the two-column primary key. */
    public static class Key implements Serializable {

        public String followerId;
        public String followeeId;

        public Key() {
        }

        public Key(String followerId, String followeeId) {
            this.followerId = followerId;
            this.followeeId = followeeId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(followerId, key.followerId)
                    && Objects.equals(followeeId, key.followeeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(followerId, followeeId);
        }
    }
}
