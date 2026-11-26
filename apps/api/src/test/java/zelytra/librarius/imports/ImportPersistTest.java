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

    @Inject
    zelytra.librarius.catalog.CatalogEntryService catalog;

    @Inject
    zelytra.librarius.domain.repository.WishlistItemRepository wishes;

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
            assertEquals(Double.valueOf(4), item.rating);
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

    private String newUser() {
        String userId = "import-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            AppUser user = new AppUser();
            user.id = userId;
            user.displayName = "Import fixture";
            em.persist(user);
        });
        return userId;
    }

    private static ImportedBook booknodeRow(String title, String rank, zelytra.librarius.domain.Kind kind,
            boolean wished, LocalDate added) {
        return new ImportedBook(title, "Some Author", null,
                wished ? zelytra.librarius.domain.LibraryStatus.TO_READ : zelytra.librarius.domain.LibraryStatus.READ,
                "manga or", 4, added, null, null, null, rank, kind, wished);
    }

    /** A Booknode library: tiers on the built-in ranks, manga as manga, wants on the wishlist, undated readings. */
    @Test
    void filesABooknodeLibraryTheWayTheReaderFiledIt() {
        String userId = newUser();
        String marker = "Booknode " + UUID.randomUUID();
        LocalDate added = LocalDate.of(2024, 5, 2);

        imports.persist(userId, "booknode", List.of(
                booknodeRow(marker + " read", "or", zelytra.librarius.domain.Kind.MANGA, false, added),
                booknodeRow(marker + " wanted", null, null, true, added)));

        QuarkusTransaction.requiringNew().run(() -> {
            List<LibraryItem> owned = items.listByUser(userId);
            assertEquals(1, owned.size());
            LibraryItem item = owned.get(0);
            assertEquals("or", item.rankCategory.code);
            org.junit.jupiter.api.Assertions.assertTrue(item.rankCategory.builtin);
            assertEquals(zelytra.librarius.domain.Kind.MANGA, item.edition.work.kind);
            // Read, and undated: the shelf date is when it was added, not when it was read (#645).
            assertEquals(Integer.valueOf(100), item.progress.percent);
            assertEquals(null, item.progress.finishedAt);
            assertEquals(added, item.acquiredAt);
            // A manga list gives no genre word, so the kind stands for one: favourite genres
            // are no longer empty for an imported library.
            assertEquals(List.of("Manga"), item.edition.work.genres.stream().map(g -> g.label).toList());
            List<zelytra.librarius.domain.WishlistItem> wished = wishes.listForExport(userId);
            assertEquals(1, wished.size());
            assertEquals(marker + " wanted", wished.get(0).edition.work.title);
        });
    }

    /**
     * An account imported before tiers, kinds and dates were read: importing the same file again
     * completes it rather than skipping every title it already holds.
     */
    @Test
    void reimportingCompletesAnOlderImport() {
        String userId = newUser();
        String title = "Completion " + UUID.randomUUID();
        LocalDate added = LocalDate.of(2023, 9, 14);
        // What the older import made of the row: a book, filed under its list, undated.
        imports.persist(userId, "booknode", List.of(new ImportedBook(title, "Some Author", null,
                zelytra.librarius.domain.LibraryStatus.READ, "manga or", 4, added)));

        ImportService.ImportResult again = imports.persist(userId, "booknode",
                List.of(booknodeRow(title, "argent", zelytra.librarius.domain.Kind.MANGA, false, added)));

        assertEquals(0, again.imported());
        assertEquals(1, again.skipped());
        QuarkusTransaction.requiringNew().run(() -> {
            List<LibraryItem> owned = items.listByUser(userId);
            assertEquals(1, owned.size());
            LibraryItem item = owned.get(0);
            assertEquals("argent", item.rankCategory.code);
            assertEquals(zelytra.librarius.domain.Kind.MANGA, item.edition.work.kind);
            assertEquals(null, item.progress.finishedAt);
        });
    }

    /**
     * #645: an earlier Booknode import took the shelf date — sometimes the day of a wish — for
     * the reading date. Importing again takes that date back, and only that one.
     */
    @Test
    void reimportingClearsTheReadingDateAnEarlierImportTookFromTheShelf() {
        String userId = newUser();
        String invented = "Shelf date " + UUID.randomUUID();
        String readerSet = "Reader date " + UUID.randomUUID();
        LocalDate added = LocalDate.of(2022, 1, 9);
        LocalDate reallyRead = LocalDate.of(2022, 6, 30);
        // What the older parser made of the rows: read on the day they were added.
        imports.persist(userId, "booknode", List.of(
                new ImportedBook(invented, "Some Author", null, zelytra.librarius.domain.LibraryStatus.READ,
                        null, 4, added, added, null, null),
                new ImportedBook(readerSet, "Some Author", null, zelytra.librarius.domain.LibraryStatus.READ,
                        null, 4, added, added, null, null)));
        // The reader then corrected the second one by hand.
        QuarkusTransaction.requiringNew().run(() -> items.listByUser(userId).stream()
                .filter(i -> i.edition.work.title.equals(readerSet))
                .forEach(i -> i.progress.finishedAt = reallyRead));

        imports.persist(userId, "booknode", List.of(
                booknodeRow(invented, null, null, false, added),
                booknodeRow(readerSet, null, null, false, added)));

        QuarkusTransaction.requiringNew().run(() -> {
            java.util.Map<String, LibraryItem> owned = new java.util.HashMap<>();
            items.listByUser(userId).forEach(i -> owned.put(i.edition.work.title, i));
            assertEquals(null, owned.get(invented).progress.finishedAt);
            assertEquals(added, owned.get(invented).acquiredAt);
            assertEquals(reallyRead, owned.get(readerSet).progress.finishedAt);
        });
    }

    @Test
    void readsAGenreOffTheReadersList() {
        assertEquals("Romance", ImportGenres.of("romance", null));
        assertEquals("Jeunesse", ImportGenres.of("Jeunesse", null));
        assertEquals("Dark romance", ImportGenres.of("dark romance", null));
        // A shelf that is not a genre stays out of the statistics.
        assertEquals(null, ImportGenres.of("maman", null));
        assertEquals(null, ImportGenres.of("Sarah JMass", null));
        assertEquals("Bande dessinée", ImportGenres.of("BD", zelytra.librarius.domain.Kind.COMIC));
    }

    private String member() {
        String userId = "import-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            AppUser user = new AppUser();
            user.id = userId;
            user.displayName = "Import fixture";
            em.persist(user);
        });
        return userId;
    }

    /** #642: Booknode spells one series two ways; its volumes still land in one. */
    @Test
    void filesTwoSpellingsOfOneSeriesUnderTheSameSeries() {
        String userId = member();
        String run = UUID.randomUUID().toString().substring(0, 8);

        imports.importFromCsv(userId, "titre;auteur;statut\n"
                + "Le Vaisseau d'Amelia " + run + ", Tome 1;Lise Auteur;à lire\n"
                + "Le Vaisseau d'Amélia " + run + " - Tome 2;Lise Auteur;à lire\n");

        QuarkusTransaction.requiringNew().run(() -> {
            List<LibraryItem> owned = items.listByUser(userId);
            assertEquals(2, owned.size());
            assertNotNull(owned.get(0).edition.work.series);
            assertEquals(owned.get(0).edition.work.series.id, owned.get(1).edition.work.series.id);
        });
    }

    /** #642: a title an earlier import left standalone joins its series when imported again. */
    @Test
    void anImportAgainFilesAnEarlierStandaloneTitleUnderItsSeries() {
        String userId = member();
        String series = "La Passe-miroir " + UUID.randomUUID().toString().substring(0, 8);
        String title = series + ", Livre 2 : Les Disparus du Clairdelune";
        QuarkusTransaction.requiringNew().run(() -> {
            LibraryItem earlier = new LibraryItem();
            earlier.userId = userId;
            earlier.edition = catalog.createManualEdition(new zelytra.librarius.web.ApiDtos.ManualBookDto(
                    zelytra.librarius.domain.Kind.BOOK, title, "Christelle Dabos", null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null), userId);
            earlier.status = zelytra.librarius.domain.LibraryStatus.TO_READ;
            items.persist(earlier);
        });

        imports.importFromCsv(userId, "titre;auteur;statut\n" + title + ";Christelle Dabos;à lire\n");

        QuarkusTransaction.requiringNew().run(() -> {
            List<LibraryItem> owned = items.listByUser(userId);
            assertEquals(1, owned.size());
            assertNotNull(owned.get(0).edition.work.series);
            assertEquals(series, owned.get(0).edition.work.series.title);
            assertEquals(Integer.valueOf(2), owned.get(0).edition.work.volumeNumber);
        });
    }
}
