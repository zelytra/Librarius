package zelytra.librarius.scan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IsbnTest {

    @Test
    void readsAnIsbn13WithItsSeparators() {
        assertEquals("9782070360024", Isbn.toIsbn13("978-2-07-036002-4"));
        assertEquals("9782070360024", Isbn.toIsbn13("ISBN 978 2 07 036002 4"));
    }

    @Test
    void convertsAnIsbn10() {
        assertEquals("9782070360024", Isbn.toIsbn13("2-07-036002-4"));
        // The X check digit of an ISBN-10.
        assertEquals("9780306406157", Isbn.toIsbn13("0306406152"));
        assertEquals("9780804429573", Isbn.toIsbn13("080442957X"));
    }

    @Test
    void refusesWhatIsNotABooksNumber() {
        // A shop's own EAN-13: valid barcode, not a Bookland prefix.
        assertNull(Isbn.toIsbn13("3017620422003"));
        // A misread: wrong check digit.
        assertNull(Isbn.toIsbn13("9782070360025"));
        assertNull(Isbn.toIsbn13("hello"));
        assertNull(Isbn.toIsbn13(null));
    }
}
