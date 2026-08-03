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
import static org.junit.jupiter.api.Assertions.assertNull;
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
