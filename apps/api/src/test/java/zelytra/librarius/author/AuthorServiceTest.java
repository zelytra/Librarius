package zelytra.librarius.author;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import zelytra.librarius.catalog.CatalogEntryService;
import zelytra.librarius.domain.Author;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.Work;
import zelytra.librarius.web.ApiDtos.ManualBookDto;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The runtime half of the author tables.
 *
 * <p>The backfill only covers what the catalog held the day the migration ran. Without this
 * service every title added afterwards would carry a credit line and no author, and
 * {@code work_author} would stop growing on the day it was created — so what is asserted here
 * is that recording an entry the ordinary way, through {@link CatalogEntryService}, credits
 * the same rows the backfill would have.
 *
 * <p>The fixtures use names nobody else in the suite writes: {@code author} is shared catalog
 * data with no user to scope it by, so a test asserting on "Isaac Asimov" would be asserting
 * on whatever the last test to mention him left behind.
 */
@QuarkusTest
class AuthorServiceTest {

    @Inject
    AuthorService authors;

    @Inject
    CatalogEntryService entries;

    @Inject
    EntityManager em;

    @Test
    void aCreditLineIsSplitIntoOneRowPerPerson() {
        String first = unique("Ada");
        String second = unique("Grace");

        Set<Author> resolved = resolve(first + ", " + second);

        assertEquals(2, resolved.size());
        assertEquals(Set.of(first, second),
                resolved.stream().map(a -> a.name).collect(Collectors.toSet()),
                "the spelling is kept as written");
        assertEquals(sorted(AuthorNormalizer.key(first), AuthorNormalizer.key(second)),
                keysOf(resolved));
    }

    /** Two entries crediting one person, spelled differently, land on the same row. */
    @Test
    void twoSpellingsOfOneNameResolveToTheSameRow() {
        String name = unique("Ekaterina");

        Set<Author> first = resolve(name);
        Set<Author> second = resolve(name.toUpperCase());

        assertEquals(1, first.size());
        assertEquals(idsOf(first), idsOf(second));
    }

    @Test
    void aBlankCreditLineNamesNobody() {
        assertEquals(Set.of(), resolve(null));
        assertEquals(Set.of(), resolve("   "));
        assertEquals(Set.of(), resolve(" , & "));
    }

    /** An author folded out of free text names no catalogue record, and has no portrait. */
    @Test
    void anAuthorFoldedOutOfFreeTextCarriesNoProviderReference() {
        Author author = resolve(unique("Nnedi")).iterator().next();

        assertNull(author.provider, "provider");
        assertNull(author.providerRef, "provider reference");
        assertNull(author.photoUrl, "photo");
    }

    // ── Through the path a real entry takes ───────────────────────────────────

    /**
     * The acceptance criterion that matters most for what comes after this issue: an entry
     * recorded today is credited, and its free-text line is still written — the clients
     * reading {@code BookView.authors} have not moved yet.
     */
    @Test
    void recordingAnEntryCreditsItsAuthorsAndKeepsTheFreeTextLine() {
        String first = unique("Ursula");
        String second = unique("Octavia");
        String creditLine = first + ", " + second;

        UUID workId = QuarkusTransaction.requiringNew()
                .call(() -> entries.createManualEdition(entry(creditLine)).work.id);

        QuarkusTransaction.requiringNew().run(() -> {
            Work work = em.find(Work.class, workId);
            assertEquals(creditLine, work.authorsText, "the free-text line is still written");
            assertEquals(sorted(AuthorNormalizer.key(first), AuthorNormalizer.key(second)),
                    keysOf(work.authors));
        });
    }

    /**
     * Two entries by the same person are credited to one author, which is what makes a
     * bibliography a bibliography rather than a list of one.
     */
    @Test
    void twoEntriesByTheSamePersonShareTheAuthorRow() {
        String name = unique("Becky");

        UUID first = QuarkusTransaction.requiringNew()
                .call(() -> entries.createManualEdition(entry(name)).work.id);
        UUID second = QuarkusTransaction.requiringNew()
                .call(() -> entries.createManualEdition(entry(name.toLowerCase())).work.id);

        assertNotEquals(first, second, "two distinct works");
        QuarkusTransaction.requiringNew().run(() -> {
            Set<UUID> firstAuthors = idsOf(em.find(Work.class, first).authors);

            assertEquals(1, firstAuthors.size());
            assertEquals(firstAuthors, idsOf(em.find(Work.class, second).authors));
        });
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private Set<Author> resolve(String creditLine) {
        return QuarkusTransaction.requiringNew().call(() -> authors.resolve(creditLine));
    }

    /** A name no other test writes, so the shared {@code author} table cannot answer for it. */
    private static String unique(String firstName) {
        return firstName + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    /** A title nothing else in the suite can collide with, credited to that line. */
    private static ManualBookDto entry(String creditLine) {
        return new ManualBookDto(Kind.BOOK, "Author fixture " + UUID.randomUUID(), creditLine,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null);
    }

    private static List<String> sorted(String... keys) {
        return List.of(keys).stream().sorted().toList();
    }

    private static List<String> keysOf(Set<Author> authors) {
        return authors.stream().map(a -> a.nameKey).sorted().toList();
    }

    private static Set<UUID> idsOf(Set<Author> authors) {
        return authors.stream().map(a -> a.id).collect(Collectors.toSet());
    }
}
