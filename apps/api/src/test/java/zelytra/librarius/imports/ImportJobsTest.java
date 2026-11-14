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

    /** A Mangacollec collection lands volume by volume, with what the site says of each (#448). */
    @Test
    void importsAMangacollecCollectionWithOwnershipFormatAndSeries() throws Exception {
        String userId = "import-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            AppUser user = new AppUser();
            user.id = userId;
            user.displayName = "Mangacollec fixture";
            em.persist(user);
        });
        String body;
        try (var in = getClass().getClassLoader().getResourceAsStream("mangacollec-collection.json")) {
            body = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }

        ImportJobs.Job job = importJobs.submit(userId, MangacollecImporter.SOURCE,
                () -> MangacollecImporter.parse(body));
        for (int i = 0; i < 100 && job.status() == ImportJobs.Status.RUNNING; i++) {
            Thread.sleep(50);
        }

        assertEquals(ImportJobs.Status.DONE, job.status());
        assertEquals(6, job.imported());
        QuarkusTransaction.requiringNew().run(() -> {
            var byTitle = items.listByUser(userId).stream().collect(java.util.stream.Collectors.toMap(
                    item -> item.edition.work.title, item -> item));
            var readNotHeld = byTitle.get("Jujutsu Kaisen Tome 3");
            assertEquals(zelytra.librarius.domain.Ownership.NOT_OWNED, readNotHeld.ownership);
            assertEquals(zelytra.librarius.domain.LibraryStatus.READ, readNotHeld.status);
            assertEquals("Jujutsu Kaisen", readNotHeld.edition.work.seriesTitle);
            assertEquals(3, readNotHeld.edition.work.volumeNumber);
            assertEquals(zelytra.librarius.domain.BookFormat.DELUXE,
                    byTitle.get("Monster Intégral Deluxe Tome 1").format);
            assertEquals(zelytra.librarius.domain.Ownership.OWNED,
                    byTitle.get("Jujutsu Kaisen Tome 2").ownership);
        });
    }
}
