package zelytra.librarius.social;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** #569: the reading time a finished book's feed post shows. */
class FeedReadingDaysTest {

    @Test
    void countsBothEnds() {
        assertEquals(10, FeedService.readingDays(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10)));
        assertEquals(1, FeedService.readingDays(java.sql.Date.valueOf("2026-03-04"), java.sql.Date.valueOf("2026-03-04")));
    }

    @Test
    void needsBothDatesInOrder() {
        assertNull(FeedService.readingDays(null, LocalDate.of(2026, 1, 10)));
        assertNull(FeedService.readingDays(LocalDate.of(2026, 1, 1), null));
        assertNull(FeedService.readingDays(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)));
    }
}
