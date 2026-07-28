package zelytra.librarius.domain.repository;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.GoalUnit;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.domain.ReadingProgress;
import zelytra.librarius.domain.Work;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Progress towards a yearly reading goal.
 *
 * <p>The figure used to be the number of titles read all years taken together, which made
 * any annual goal meaningless: a reader with a hundred books behind them met a thirty-book
 * target on the first of January. It is now measured over the year, off the date the title
 * was finished, and in the unit the goal is expressed in.
 */
@QuarkusTest
class ReadingYearTest {

    @Inject
    LibraryItemRepository library;

    @Inject
    EntityManager em;

    @Test
    void countsOnlyWhatWasFinishedDuringTheYear() {
        String userId = seed(
                finished(LocalDate.of(2031, 3, 4), 300),
                finished(LocalDate.of(2031, 12, 31), 150),
                finished(LocalDate.of(2030, 12, 31), 999),
                finished(LocalDate.of(2032, 1, 1), 999));

        assertEquals(2, library.readInYear(userId, 2031, GoalUnit.BOOKS));
    }

    /** The boundaries belong to the year they fall in, and to that year only. */
    @Test
    void includesBothEndsOfTheYear() {
        String userId = seed(
                finished(LocalDate.of(2031, 1, 1), 100),
                finished(LocalDate.of(2031, 12, 31), 100));

        assertEquals(2, library.readInYear(userId, 2031, GoalUnit.BOOKS));
        assertEquals(0, library.readInYear(userId, 2030, GoalUnit.BOOKS));
        assertEquals(0, library.readInYear(userId, 2032, GoalUnit.BOOKS));
    }

    /**
     * A title read but never dated contributes nothing: the status says a book has been
     * read, never when — a bulk import would otherwise land entirely on the current year.
     */
    @Test
    void ignoresTitlesWithNoFinishDate() {
        String userId = seed(
                finished(LocalDate.of(2031, 6, 1), 200),
                finished(null, 500));

        assertEquals(1, library.readInYear(userId, 2031, GoalUnit.BOOKS));
    }

    @Test
    void pagesSumThePageCountsOfTheYear() {
        String userId = seed(
                finished(LocalDate.of(2031, 2, 2), 300),
                finished(LocalDate.of(2031, 8, 8), 150),
                finished(LocalDate.of(2030, 8, 8), 999));

        assertEquals(450, library.readInYear(userId, 2031, GoalUnit.PAGES));
    }

    /** An edition declaring no page count contributes zero rather than blowing the sum up. */
    @Test
    void pagesToleratesEditionsWithoutAPageCount() {
        String userId = seed(
                finished(LocalDate.of(2031, 2, 2), 300),
                finished(LocalDate.of(2031, 5, 5), null));

        assertEquals(300, library.readInYear(userId, 2031, GoalUnit.PAGES));
        assertEquals(2, library.readInYear(userId, 2031, GoalUnit.VOLUMES));
    }

    @Test
    void countsNothingForAUserWhoReadNothing() {
        String userId = seed();

        assertEquals(0, library.readInYear(userId, 2031, GoalUnit.BOOKS));
        assertEquals(0, library.readInYear(userId, 2031, GoalUnit.PAGES));
    }

    /** Someone else's reading never counts towards the caller's goal. */
    @Test
    void isScopedToTheUser() {
        String mine = seed(finished(LocalDate.of(2031, 4, 4), 100));
        seed(finished(LocalDate.of(2031, 4, 4), 100), finished(LocalDate.of(2031, 5, 5), 100));

        assertEquals(1, library.readInYear(mine, 2031, GoalUnit.BOOKS));
    }

    // ── Fixture ───────────────────────────────────────────────────────────────

    /** One finished title: the day it was finished, and the page count of its edition. */
    private record Finished(LocalDate finishedAt, Integer pageCount) {
    }

    private static Finished finished(LocalDate finishedAt, Integer pageCount) {
        return new Finished(finishedAt, pageCount);
    }

    /** Inserts the titles for a brand new user and returns their identifier. */
    private String seed(Finished... titles) {
        String userId = "goal-" + UUID.randomUUID();

        QuarkusTransaction.requiringNew().run(() -> {
            // library_item.user_id is a foreign key onto app_user.
            AppUser user = new AppUser();
            user.id = userId;
            user.displayName = "Goal fixture";
            em.persist(user);

            for (Finished title : titles) {
                Work work = new Work();
                work.kind = Kind.BOOK;
                work.title = "Goal " + UUID.randomUUID();
                em.persist(work);

                Edition edition = new Edition();
                edition.work = work;
                edition.pageCount = title.pageCount();
                em.persist(edition);

                LibraryItem item = new LibraryItem();
                item.userId = userId;
                item.edition = edition;
                item.status = LibraryStatus.READ;
                em.persist(item);

                ReadingProgress progress = new ReadingProgress();
                progress.libraryItem = item;
                progress.finishedAt = title.finishedAt();
                em.persist(progress);
            }
        });

        return userId;
    }
}
