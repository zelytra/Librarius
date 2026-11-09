package zelytra.librarius.imports;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import zelytra.librarius.catalog.CatalogResult;
import zelytra.librarius.catalog.CatalogService;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.Work;
import zelytra.librarius.domain.repository.EditionRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

/**
 * The enricher writing a catalog page count onto an imported edition, with the provider mocked
 * so the suite never reaches out to a catalog.
 */
@QuarkusTest
class ImportEnricherTest {

    @InjectMock
    CatalogService catalog;

    @Inject
    ImportEnricher enricher;

    @Inject
    EditionRepository editions;

    @Inject
    EntityManager em;

    /** A page count of 384 for our title; other editions in the batch get the same answer but,
     *  their titles not matching it, keep their empty length. */
    private CatalogResult resultFor(String title, int pages) {
        return new CatalogResult("BOOK", title, "C. S. Quill", 2020, null, null, null, null, null,
                null, null, null, pages, "openlibrary", null);
    }

    @Test
    void fillsThePageCountOfAnImportedEditionFromTheCatalog() {
        String title = "Enricher Abysses " + UUID.randomUUID();
        UUID editionId = seedImportedEdition(title);

        Mockito.when(catalog.search(any(), any(), anyInt())).thenReturn(List.of(resultFor(title, 384)));

        enricher.enrichNow();

        Edition after = QuarkusTransaction.requiringNew().call(() -> editions.findById(editionId));
        assertEquals(Integer.valueOf(384), after.pageCount);
    }

    @Test
    void leavesAnEditionAloneWhenTheCatalogMatchIsADifferentTitle() {
        String title = "Enricher Only Mine " + UUID.randomUUID();
        UUID editionId = seedImportedEdition(title);

        // The catalog answers, but with a title that is not this one — a length taken from the
        // wrong book is worse than none, so it is left to try again another day.
        Mockito.when(catalog.search(any(), any(), anyInt()))
                .thenReturn(List.of(resultFor("A Completely Different Book", 512)));

        enricher.enrichNow();

        Edition after = QuarkusTransaction.requiringNew().call(() -> editions.findById(editionId));
        assertNull(after.pageCount);
    }

    @Test
    void fillsTheCoverIsbnAndPublisherOfAnImportedEdition() {
        String title = "Enricher Cover " + UUID.randomUUID();
        UUID editionId = seedImportedEdition(title);
        CatalogResult complete = new CatalogResult("BOOK", title, "C. S. Quill", 2020,
                "https://covers.openlibrary.org/b/id/42-M.jpg", null, "9782070342266", "Gallimard",
                null, null, null, null, 300, "openlibrary", "OL1W");
        Mockito.when(catalog.search(any(), any(), anyInt())).thenReturn(List.of(complete));

        enricher.enrichNow();

        Edition after = QuarkusTransaction.requiringNew().call(() -> editions.findById(editionId));
        assertEquals("https://covers.openlibrary.org/b/id/42-M.jpg", after.coverUrl);
        assertEquals("9782070342266", after.isbn13);
        assertEquals("Gallimard", after.publisher);
        assertEquals(Integer.valueOf(300), after.pageCount);
    }

    @Test
    void stampsAnUnmatchedEditionSoItStopsHoldingTheQueue() {
        String title = "Enricher Unknown " + UUID.randomUUID();
        UUID editionId = seedImportedEdition(title);
        Mockito.when(catalog.search(any(), any(), anyInt())).thenReturn(List.of());

        enricher.enrichNow();

        Edition after = QuarkusTransaction.requiringNew().call(() -> editions.findById(editionId));
        assertNotNull(after.enrichAttemptedAt);
        // Stamped just now, so not due again before the cooldown has passed.
        List<UUID> due = QuarkusTransaction.requiringNew().call(() -> editions
                .needingEnrichment(java.time.OffsetDateTime.now().minus(ImportEnricher.RETRY_AFTER), 500)
                .stream().map(e -> e.id).toList());
        assertFalse(due.contains(editionId));
    }

    @Test
    void matchesAVolumeAcrossTwoWordings() {
        ImportEnricher.Target onePiece = new ImportEnricher.Target(UUID.randomUUID(), Kind.BOOK,
                "One Piece Tome 1", "Eiichiro Oda", "One Piece", 1);

        assertTrue(ImportEnricher.sameBook(onePiece, new CatalogResult("BOOK",
                "One piece. 1, East blue", "Eiichirō Oda", 2021, null, null, null, null, null, null,
                "One piece", 1, null, "bnf", "ark")));
        assertFalse(ImportEnricher.sameBook(onePiece, new CatalogResult("BOOK",
                "One Piece. 113", "Eiichirō Oda", 2026, null, null, null, null, null, null,
                "One Piece", 113, null, "bnf", "ark")));
    }

    @Test
    void doesNotTakeTheCoverOfASequelOrOfSomebodyElsesBook() {
        ImportEnricher.Target dune = new ImportEnricher.Target(UUID.randomUUID(), Kind.BOOK,
                "Dune", "Frank Herbert", null, null);

        assertTrue(ImportEnricher.sameBook(dune, book("Dune", "Frank Herbert")));
        assertTrue(ImportEnricher.sameBook(dune, book("Dune : roman", "Frank Herbert")));
        assertFalse(ImportEnricher.sameBook(dune, book("Dune Messiah", "Frank Herbert")));
        assertFalse(ImportEnricher.sameBook(dune, book("Dune", "Someone Else")));
    }

    @Test
    void prefersTheFrenchEditionAmongMatches() {
        CatalogResult audiobook = new CatalogResult("BOOK", "Dune", "Frank Herbert", 1965,
                "https://covers.openlibrary.org/b/id/1-M.jpg", null, "9780425043769", "Books on Tape",
                "eng", null, "openlibrary", "OL1W");
        CatalogResult french = new CatalogResult("BOOK", "Dune", "Frank Herbert", 1970, null, null,
                "9782266116242", "Pocket", "fre", null, "bnf", "ark");

        assertEquals("Pocket", java.util.stream.Stream.of(audiobook, french)
                .min(ImportEnricher.PREFERRED).orElseThrow().publisher());
    }

    private static CatalogResult book(String title, String authors) {
        return new CatalogResult("BOOK", title, authors, null, null, null, null, null, null, null,
                "openlibrary", "OL1W");
    }

    /** An edition with no page count and no provider reference — exactly what a scrape leaves. */
    private UUID seedImportedEdition(String title) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Work work = new Work();
            work.kind = Kind.BOOK;
            work.title = title;
            work.authorsText = "C. S. Quill";
            em.persist(work);
            Edition edition = new Edition();
            edition.work = work;
            em.persist(edition);
            return edition.id;
        });
    }
}
