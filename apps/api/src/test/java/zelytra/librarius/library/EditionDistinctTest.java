package zelytra.librarius.library;

import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.Edition;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The editions a work's screen offers: no empty ones, no twins. */
class EditionDistinctTest {

    private static Edition edition(String publisher, String isbn) {
        Edition e = new Edition();
        e.id = UUID.randomUUID();
        e.publisher = publisher;
        e.isbn13 = isbn;
        return e;
    }

    @Test
    void offersNoEditionThatDescribesNothingAndNoTwin() {
        Edition mine = edition(null, null);
        Edition othersBare = edition(null, null);
        Edition anotherBare = edition(null, null);
        Edition folio = edition("Folio", "9782070360024");
        Edition folioAgain = edition("Folio", "978-2-07-036002-4");
        Edition pocket = edition("Pocket", null);

        List<Edition> offered = EditionService.distinct(
                List.of(mine, othersBare, anotherBare, folio, folioAgain, pocket), Set.of(mine.id));

        // The reader's own bare edition stays — it is theirs — the other readers' do not.
        assertEquals(List.of(mine, folio, pocket), offered);
    }
}
