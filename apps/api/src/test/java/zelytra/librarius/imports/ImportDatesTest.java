package zelytra.librarius.imports;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Import date parsing: ISO, numeric and the French "12 mars 2024" form. */
class ImportDatesTest {

    @Test
    void parsesTheFormsTheSourcesUse() {
        assertEquals(LocalDate.of(2024, 3, 12), ImportDates.parse("2024-03-12"));
        assertEquals(LocalDate.of(2024, 3, 12), ImportDates.parse("12/03/2024"));
        assertEquals(LocalDate.of(2024, 3, 12), ImportDates.parse("12 mars 2024"));
        assertEquals(LocalDate.of(2024, 8, 1), ImportDates.parse("1 août 2024"));
    }

    @Test
    void answersNullRatherThanFailingOnWhatItCannotRead() {
        assertNull(ImportDates.parse(null));
        assertNull(ImportDates.parse(""));
        assertNull(ImportDates.parse("not a date"));
        assertNull(ImportDates.parse("12 foo 2024"));
    }
}
