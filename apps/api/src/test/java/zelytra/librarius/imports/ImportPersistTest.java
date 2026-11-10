package zelytra.librarius.imports;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.repository.LibraryItemRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The import writing a title's rating, acquisition date and shelf-category through to the item. */
@QuarkusTest
class ImportPersistTest {

    @Inject
    ImportService imports;

    @Inject
    LibraryItemRepository items;

    @Inject
    EntityManager em;

    @Test
    void importsRatingDateAndAShelfCategory() {
        String userId = "import-" + UUID.randomUUID();
        // library_item.user_id and rank_category.user_id are both foreign keys onto app_user, so
        // the account has to exist before its library can be imported — as it does in production,
        // where the row is created the first time the member signs in.
        QuarkusTransaction.requiringNew().run(() -> {
            AppUser user = new AppUser();
            user.id = userId;
            user.displayName = "Import fixture";
            em.persist(user);
        });

        String csv = """
                Title,Author,Exclusive Shelf,My Rating,Date Read
                Fourth Wing,Rebecca Yarros,favorites,4,2024-03-12
                """;

        imports.importFromCsv(userId, csv);

        QuarkusTransaction.requiringNew().run(() -> {
            List<LibraryItem> owned = items.listByUser(userId);
            assertEquals(1, owned.size());
            LibraryItem item = owned.get(0);
            assertEquals(Integer.valueOf(4), item.rating);
            // A dated reading: the title is read, finished on that day, and counted in the
            // reading history rather than filed as an acquisition.
            assertEquals(zelytra.librarius.domain.LibraryStatus.READ, item.status);
            assertNotNull(item.progress);
            assertEquals(LocalDate.of(2024, 3, 12), item.progress.finishedAt);
            assertEquals(Integer.valueOf(100), item.progress.percent);
            // "favorites" is not a reading state, so it becomes a category on the item.
            assertNotNull(item.rankCategory);
            assertEquals("favorites", item.rankCategory.label);
        });
    }

    @Test
    void filesAnImportedVolumeUnderItsSeries() {
        String userId = "import-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            AppUser user = new AppUser();
            user.id = userId;
            user.displayName = "Import fixture";
            em.persist(user);
        });
        String series = "Import Saga " + UUID.randomUUID();

        imports.importFromCsv(userId, "titre;auteur;statut\n" + series + " Tome 2;A. Author;à lire\n");

        QuarkusTransaction.requiringNew().run(() -> {
            LibraryItem item = items.listByUser(userId).get(0);
            assertNotNull(item.edition.work.series);
            assertEquals(series, item.edition.work.series.title);
            assertEquals(Integer.valueOf(2), item.edition.work.volumeNumber);
            // Not read: no reading is recorded.
            assertEquals(null, item.progress);
        });
    }

    /**
     * A library bigger than one batch: written in several transactions — a single one ran past
     * the 60-second transaction timeout on a 2346-title Booknode library and was rolled back
     * whole — with the progress reported after each, and one shelf category shared by titles
     * that land in different batches rather than created once per batch.
     */
    @Test
    void writesABigLibraryInBatchesAndReportsProgress() {
        String userId = "import-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            AppUser user = new AppUser();
            user.id = userId;
            user.displayName = "Import fixture";
            em.persist(user);
        });
        int size = ImportService.BATCH * 2 + 17;
        List<ImportedBook> books = new java.util.ArrayList<>();
        for (int i = 0; i < size; i++) {
            books.add(new ImportedBook("Big Library Title " + i, "Author " + i, null,
                    zelytra.librarius.domain.LibraryStatus.READ, "romance", 4, null));
        }
        List<int[]> reported = new java.util.ArrayList<>();

        ImportService.ImportResult result = imports.persist(userId, "booknode", books,
                (imported, skipped) -> reported.add(new int[] {imported, skipped}));

        assertEquals(size, result.imported());
        // One report per batch, each a running total.
        assertEquals(List.of(ImportService.BATCH, ImportService.BATCH * 2, size),
                reported.stream().map(r -> r[0]).toList());
        QuarkusTransaction.requiringNew().run(() -> {
            List<LibraryItem> owned = items.listByUser(userId);
            assertEquals(size, owned.size());
            assertEquals(1, owned.stream().map(i -> i.rankCategory.id).distinct().count());
        });

        // Running it again finds every title already there.
        ImportService.ImportResult again = imports.persist(userId, "booknode", books);
        assertEquals(0, again.imported());
        assertEquals(size, again.skipped());
    }
}
