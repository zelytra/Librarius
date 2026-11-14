package zelytra.librarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** One placed entry of a view's manual order (#451): a title or a series block, at a position. */
@Entity
@Table(name = "library_view_entry")
public class LibraryViewEntry {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "view_id", nullable = false)
    public UUID viewId;

    @Column(nullable = false)
    public int position;

    @Column(name = "item_id")
    public UUID itemId;

    @Column(name = "series_id")
    public UUID seriesId;
}
