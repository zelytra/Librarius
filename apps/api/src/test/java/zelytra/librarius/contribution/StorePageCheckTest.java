package zelytra.librarius.contribution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What a store page must carry to confirm a book (#620): its ISBN and its title. */
class StorePageCheckTest {

    private static final String PAGE = """
            <html><head><title>Le Petit Prince - Broché - Antoine de Saint-Exupéry - Achat Livre | fnac</title>
            <script type="application/ld+json">{"@type":"Book","isbn":"9782070612758"}</script></head>
            <body><h1>Le Petit Prince</h1><dl><dt>EAN</dt><dd>978-2-07-061275-8</dd></dl></body></html>
            """;

    @Test
    void aPageCarryingTheIsbnAndTheTitleConfirmsTheBook() {
        assertEquals(StorePageCheck.Outcome.PASSED, StorePageCheck.check(PAGE, "9782070612758", null, "Le Petit Prince"));
        // Accents, case and punctuation do not matter.
        assertEquals(StorePageCheck.Outcome.PASSED, StorePageCheck.check(PAGE, "9782070612758", null, "le petit-prince"));
        assertEquals(StorePageCheck.Outcome.PASSED,
                StorePageCheck.check(PAGE.replace("Petit", "Pétit"), "9782070612758", null, "Le Petit Prince"));
    }

    @Test
    void theIsbnMayBeWrittenWithHyphensOrAsAnIsbn10() {
        String hyphens = "<html><title>Le Petit Prince</title><p>ISBN 978-2-07-061275-8</p></html>";
        assertEquals(StorePageCheck.Outcome.PASSED, StorePageCheck.check(hyphens, "9782070612758", null, "Le Petit Prince"));
        String isbn10 = "<html><title>Le Petit Prince</title><p>ISBN-10 : 2070612759</p></html>";
        assertEquals(StorePageCheck.Outcome.PASSED, StorePageCheck.check(isbn10, "9782070612758", "2070612759", "Le Petit Prince"));
    }

    @Test
    void aPageWithoutTheIsbnOrTheTitleDoesNot() {
        assertEquals(StorePageCheck.Outcome.ISBN_NOT_FOUND, StorePageCheck.check(PAGE, "9782070000000", null, "Le Petit Prince"));
        assertEquals(StorePageCheck.Outcome.TITLE_NOT_FOUND, StorePageCheck.check(PAGE, "9782070612758", null, "Vol de nuit"));
        // A longer ISBN containing this one is not this one.
        String longer = "<html><title>Le Petit Prince</title><p>197820706127589</p></html>";
        assertEquals(StorePageCheck.Outcome.ISBN_NOT_FOUND, StorePageCheck.check(longer, "9782070612758", null, "Le Petit Prince"));
    }

    @Test
    void aBookWithoutIsbnCannotBeConfirmed() {
        assertEquals(StorePageCheck.Outcome.NO_ISBN, StorePageCheck.check(PAGE, null, null, "Le Petit Prince"));
    }
}
