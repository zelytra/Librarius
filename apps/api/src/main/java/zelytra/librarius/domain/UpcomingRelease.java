package zelytra.librarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An announced release of one volume of a series, on one market.
 *
 * <p>Shared catalog data, like {@link Series} and {@link Edition}: it says what is coming
 * out, never who is waiting for it. What makes a list of these personal is the join done at
 * read time against the caller's own collection, wishlist and follows — no field here is
 * user-scoped, and none must ever become one.
 *
 * <p>Rows are written by {@code UpcomingReleaseRefresher}, off the request path: the point
 * of the table is that displaying "what is coming" costs a query, not a call to a provider
 * whose quota the whole instance shares.
 */
@Entity
@Table(name = "upcoming_release")
public class UpcomingRelease {

    /** Value standing for "entered by hand" — the source the refresher never overwrites. */
    public static final String SOURCE_MANUAL = "manual";

    /** Value standing for a date read off an edition the catalog already holds. */
    public static final String SOURCE_CATALOG = "catalog";

    @Id
    @GeneratedValue
    public UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "series_id")
    public Series series;

    /** {@code null} when the announcement names no volume — a series start, a one-shot. */
    @Column(name = "volume_number")
    public Integer volumeNumber;

    @Column(length = 512)
    public String title;

    /**
     * First day of the window the announcement opens, or {@code null} when the volume is
     * announced with no date at all. Always read together with {@link #datePrecision}.
     */
    @Column(name = "release_date")
    public LocalDate releaseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "date_precision", length = 8)
    public DatePrecision datePrecision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    public ReleaseRegion region;

    @Column(length = 255)
    public String publisher;

    /** {@link #SOURCE_MANUAL}, {@link #SOURCE_CATALOG}, or the name of a provider. */
    @Column(nullable = false, length = 32)
    public String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public ReleaseConfidence confidence;

    @Column(name = "created_at", insertable = false, updatable = false)
    public OffsetDateTime createdAt;

    /** When the announcement was last confirmed by whatever feeds it. */
    @Column(name = "updated_at")
    public OffsetDateTime updatedAt;

    /**
     * Whether the release is still ahead on the given day.
     *
     * <p>Compared against the end of the window rather than against the stored anchor: a
     * volume announced for March 2027 is still ahead on 20 March, where the anchor alone
     * (1 March) would have dropped it on the 2nd.
     *
     * <p>An announcement carrying no date is always ahead — it is precisely a volume known
     * to be coming and not known to be out.
     */
    public boolean stillAhead(LocalDate today) {
        if (releaseDate == null || datePrecision == null) {
            return true;
        }
        return !datePrecision.windowEnd(releaseDate).isBefore(today);
    }

    /** Whether the row was curated by hand, and must therefore survive a refresh. */
    public boolean isManual() {
        return SOURCE_MANUAL.equals(source);
    }
}
