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
 * A way of laying out the library, saved by the reader under a name (#451): the kind and shelf
 * it opens on, its sort ({@code manual} for the order they dragged the entries into, see
 * {@link LibraryViewEntry}), series as blocks or volume by volume, and whether it is split
 * into one section per category. Private to its owner.
 */
@Entity
@Table(name = "library_view")
public class LibraryView {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "user_id", nullable = false, length = 255)
    public String userId;

    @Column(nullable = false, length = 60)
    public String name;

    @Column(nullable = false)
    public int position;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    public Kind kind;

    /** The shelf, as the library's {@code shelf} parameter spells it ({@code rank:or}…); null for all. */
    @Column(length = 80)
    public String shelf;

    @Column(nullable = false, length = 16)
    public String sort = "recent";

    /** {@code grouped} (series as one block) or {@code flat} (every title). */
    @Column(nullable = false, length = 16)
    public String layout = "grouped";

    @Column(name = "group_by_category", nullable = false)
    public boolean groupByCategory;

    @Column(name = "created_at", insertable = false, updatable = false)
    public OffsetDateTime createdAt;
}
