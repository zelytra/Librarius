package zelytra.librarius.imports;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
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

    @Test
    void importsRatingDateAndAShelfCategory() {
        String userId = "import-" + UUID.randomUUID();
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
            assertEquals(LocalDate.of(2024, 3, 12), item.acquiredAt);
            // "favorites" is not a reading state, so it becomes a category on the item.
            assertNotNull(item.rankCategory);
            assertEquals("favorites", item.rankCategory.label);
        });
    }
}
