package zelytra.librarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * One member blocking another (#203): the account that pressed the button, and the account it
 * was pressed on.
 *
 * <p>Stored one-directionally — the (blocker, blocked) pair is the identity, the same shape
 * {@link UserFollow} uses — but read symmetrically: the predicate "is there a block between A
 * and B" tests both orderings, so a single row hides content in both directions. Only the
 * blocker knows the block exists; the blocked party sees the blocker's content become
 * unavailable, no more than that.
 */
@Entity
@Table(name = "user_block")
@IdClass(UserBlock.Key.class)
public class UserBlock {

    @jakarta.persistence.Id
    @Column(name = "blocker_id", length = 255)
    public String blockerId;

    @jakarta.persistence.Id
    @Column(name = "blocked_id", length = 255)
    public String blockedId;

    @Column(name = "created_at", insertable = false, updatable = false)
    public OffsetDateTime createdAt;

    /** Composite identifier of a block, required by JPA for the two-column primary key. */
    public static class Key implements Serializable {

        public String blockerId;
        public String blockedId;

        public Key() {
        }

        public Key(String blockerId, String blockedId) {
            this.blockerId = blockerId;
            this.blockedId = blockedId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(blockerId, key.blockerId)
                    && Objects.equals(blockedId, key.blockedId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(blockerId, blockedId);
        }
    }
}
