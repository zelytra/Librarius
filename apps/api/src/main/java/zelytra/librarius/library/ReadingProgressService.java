package zelytra.librarius.library;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.domain.ReadingProgress;
import zelytra.librarius.domain.repository.ReadingProgressRepository;
import zelytra.librarius.web.ApiDtos.ProgressDto;

import java.time.LocalDate;

/**
 * The rules that tie a reading position to the status of a title.
 *
 * <p>They live here rather than in the resource, and rather than in the front end, because
 * three things must agree: what the user typed, what the two other screens display, and
 * what a re-read of the row says. A percentage computed in the browser would be right on
 * the screen that computed it and absent everywhere else.
 */
@ApplicationScoped
public class ReadingProgressService {

    /** Reaching the end of a book is 100 %, whatever the page count says. */
    private static final int COMPLETE_PERCENT = 100;

    @Inject
    ReadingProgressRepository progresses;

    /**
     * Applies a progress update to an owned title, creating its progress row on first use.
     *
     * <p>The payload replaces the position as a whole; the status transitions then fill in
     * what the user cannot be expected to type:
     *
     * <ul>
     *   <li>{@code READING} stamps the start date when there is not one yet — the day the
     *       book was opened is the day the button was pressed — and clears the finish date:
     *       a title being read again is not a finished one, and it must stop counting
     *       towards the year it was first finished in, so the annual goal and the reading
     *       timeline stay accurate;</li>
     *   <li>{@code READ} finishes the book: 100 %, the last page when the edition has a
     *       page count, and today as the finish date unless one was supplied;</li>
     *   <li>{@code OWNED} clears the finish date — reverting to "not read" cannot leave one
     *       behind.</li>
     * </ul>
     *
     * @param item the caller's item — ownership is checked before this is reached
     * @param dto  the new position, either side of which may be left out
     */
    public void apply(LibraryItem item, ProgressDto dto) {
        if (dto.status() != null) {
            item.status = dto.status();
        }

        ReadingProgress progress = progresses.findByItem(item.id).orElseGet(() -> {
            ReadingProgress created = new ReadingProgress();
            created.libraryItem = item;
            progresses.persist(created);
            item.progress = created;
            return created;
        });

        Integer pageCount = item.edition != null ? item.edition.pageCount : null;
        // Whichever side the user filled in, the other is derived: the two must never
        // disagree once stored.
        Integer page = dto.currentPage() != null ? dto.currentPage()
                : ReadingProgress.pageOf(dto.percent(), pageCount);
        Integer percent = dto.percent() != null ? dto.percent()
                : ReadingProgress.percentOf(dto.currentPage(), pageCount);

        progress.currentPage = page;
        progress.percent = percent;
        progress.startedAt = dto.startedAt();
        progress.finishedAt = dto.finishedAt();

        LocalDate today = LocalDate.now();
        if (item.status == LibraryStatus.READING) {
            if (progress.startedAt == null) {
                progress.startedAt = today;
            }
            progress.finishedAt = null;
        }
        if (item.status == LibraryStatus.READ) {
            progress.percent = COMPLETE_PERCENT;
            if (pageCount != null) {
                progress.currentPage = pageCount;
            }
            if (progress.finishedAt == null) {
                progress.finishedAt = today;
            }
        }
        if (item.status == LibraryStatus.OWNED) {
            progress.finishedAt = null;
        }
    }
}
