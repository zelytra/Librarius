package zelytra.librarius.imports;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.domain.repository.LibraryItemRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The deferred import running to completion on its own pool and landing the titles. */
@QuarkusTest
class ImportJobsTest {

    @Inject
    ImportJobs importJobs;

    @Inject
    LibraryItemRepository items;

    @Inject
    EntityManager em;

    @Test
    void runsACsvImportInTheBackgroundToDone() throws InterruptedException {
        String userId = "import-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            AppUser user = new AppUser();
            user.id = userId;
            user.displayName = "Import fixture";
            em.persist(user);
        });

        String csv = """
                Title,Author,Exclusive Shelf
                Fourth Wing,Rebecca Yarros,read
                """;

        ImportJobs.Job job = importJobs.submit(userId, "csv", null, csv);

        // The work runs on ImportJobs' pool; wait, bounded, for it to finish rather than
        // asserting against a job that is still RUNNING.
        for (int i = 0; i < 100 && job.status() == ImportJobs.Status.RUNNING; i++) {
            Thread.sleep(50);
        }

        assertEquals(ImportJobs.Status.DONE, job.status());
        assertEquals(1, job.total());
        assertEquals(1, job.imported());
        QuarkusTransaction.requiringNew().run(() ->
                assertEquals(1, items.listByUser(userId).size()));
    }
}
