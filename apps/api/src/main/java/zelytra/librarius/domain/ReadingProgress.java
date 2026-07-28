package zelytra.librarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/** Reading progress of an owned title (1-1 with library_item). */
@Entity
@Table(name = "reading_progress")
public class ReadingProgress {

    /** Bounds of {@link #percent}, shared by the write path and the derived values. */
    private static final int MIN_PERCENT = 0;
    private static final int MAX_PERCENT = 100;

    @Id
    @GeneratedValue
    public UUID id;

    @OneToOne(optional = false)
    @JoinColumn(name = "library_item_id")
    public LibraryItem libraryItem;

    @Column(name = "current_page")
    public Integer currentPage;

    public Integer percent;

    @Column(name = "started_at")
    public LocalDate startedAt;

    @Column(name = "finished_at")
    public LocalDate finishedAt;

    /**
     * Percentage matching a page number, {@code null} when the edition carries no page
     * count — an unknown total makes the ratio meaningless rather than zero.
     *
     * <p>Page 120 of a 300-page book is 40 %. The conversion lives here rather than in the
     * front end so that every client, and every screen, reads the same figure: a user
     * entering a page on the detail screen must see the same percentage on the home
     * carousel and in a future export.
     */
    public static Integer percentOf(Integer currentPage, Integer pageCount) {
        if (currentPage == null || pageCount == null || pageCount <= 0) {
            return null;
        }
        return clampPercent((int) Math.round(currentPage * 100.0 / pageCount));
    }

    /** The page a percentage lands on, the inverse of {@link #percentOf}. */
    public static Integer pageOf(Integer percent, Integer pageCount) {
        if (percent == null || pageCount == null || pageCount <= 0) {
            return null;
        }
        long page = Math.round(clampPercent(percent) * pageCount / 100.0);
        return (int) Math.max(0, Math.min(pageCount, page));
    }

    private static int clampPercent(int percent) {
        return Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, percent));
    }
}
