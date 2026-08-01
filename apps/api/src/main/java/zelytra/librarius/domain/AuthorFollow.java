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
 * A user following an author: they want to hear about what that person publishes next.
 *
 * <p>The pair (user, author) is the identity — there is nothing else to carry, so no
 * surrogate key is introduced. Same shape as {@link SeriesFollow}, deliberately: the two
 * follows answer the same question about two different objects.
 */
@Entity
@Table(name = "author_follow")
@IdClass(AuthorFollow.Key.class)
public class AuthorFollow {

    @jakarta.persistence.Id
    @Column(name = "user_id", length = 255)
    public String userId;

    @jakarta.persistence.Id
    @Column(name = "author_id")
    public UUID authorId;

    @Column(name = "created_at", insertable = false, updatable = false)
    public OffsetDateTime createdAt;

    /** Composite identifier of a follow, required by JPA for the two-column primary key. */
    public static class Key implements Serializable {

        public String userId;
        public UUID authorId;

        public Key() {
        }

        public Key(String userId, UUID authorId) {
            this.userId = userId;
            this.authorId = authorId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(userId, key.userId) && Objects.equals(authorId, key.authorId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, authorId);
        }
    }
}
