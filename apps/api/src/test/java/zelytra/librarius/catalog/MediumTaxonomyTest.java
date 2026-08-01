package zelytra.librarius.catalog;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.Work;
import zelytra.librarius.domain.repository.WorkRepository;
import zelytra.librarius.web.ApiDtos.ManualBookDto;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The three media #178 adds -- COMIC, GRAPHIC_NOVEL, AUDIOBOOK -- have to travel the ordinary
 * entry path exactly as BOOK and MANGA do. {@link WorkRepository#findMatch} is generic over
 * the kind today, but nothing locks that down, so this pins it: the new values persist and
 * round-trip, they deduplicate a second entry onto the first work, and they never collapse a
 * title of one medium into the same title of another.
 *
 * <p>The fixtures use titles nobody else in the suite writes: {@code work} is shared catalog
 * data with no user to scope it by, so a fixed title would match whatever an earlier test
 * left behind.
 */
@QuarkusTest
class MediumTaxonomyTest {

    @Inject
    CatalogEntryService entries;

    @Inject
    WorkRepository works;

    @Inject
    EntityManager em;

    /** Each new value survives the DTO -> Work round-trip unchanged. */
    @Test
    void theNewKindsPersistAndRoundTrip() {
        for (Kind kind : new Kind[] {Kind.COMIC, Kind.GRAPHIC_NOVEL, Kind.AUDIOBOOK}) {
            String title = unique(kind.name());

            UUID workId = QuarkusTransaction.requiringNew()
                    .call(() -> entries.createManualEdition(entry(kind, title)).work.id);

            QuarkusTransaction.requiringNew().run(() ->
                    assertEquals(kind, em.find(Work.class, workId).kind,
                            "the kind is stored and read back as written"));
        }
    }

    /**
     * A second entry for the same title under a new kind matches the first work rather than
     * founding another -- the one-work-to-many-editions dedup that BOOK and MANGA already get.
     */
    @Test
    void aSecondEntryOfANewKindMatchesTheFirstWork() {
        Kind kind = Kind.COMIC;
        String title = unique("dedup");

        UUID first = QuarkusTransaction.requiringNew()
                .call(() -> entries.createManualEdition(entry(kind, title)).work.id);
        UUID second = QuarkusTransaction.requiringNew()
                .call(() -> entries.createManualEdition(entry(kind, title)).work.id);

        assertEquals(first, second, "the second entry lands on the same work");
        QuarkusTransaction.requiringNew().run(() ->
                assertEquals(first, works.findMatch(kind, title, "Fixture author", null)
                        .orElseThrow().id, "findMatch answers with that work"));
    }

    /**
     * The kind is part of the matching key: the same title entered as an audiobook and as a
     * graphic novel are two works, the same way a novel and a manga of one name already are.
     */
    @Test
    void oneTitleUnderTwoKindsIsTwoWorks() {
        String title = unique("shared-title");

        UUID audiobook = QuarkusTransaction.requiringNew()
                .call(() -> entries.createManualEdition(entry(Kind.AUDIOBOOK, title)).work.id);
        UUID graphicNovel = QuarkusTransaction.requiringNew()
                .call(() -> entries.createManualEdition(entry(Kind.GRAPHIC_NOVEL, title)).work.id);

        assertNotEquals(audiobook, graphicNovel, "a different medium is a different work");
        QuarkusTransaction.requiringNew().run(() -> {
            assertEquals(audiobook,
                    works.findMatch(Kind.AUDIOBOOK, title, "Fixture author", null).orElseThrow().id);
            assertEquals(graphicNovel,
                    works.findMatch(Kind.GRAPHIC_NOVEL, title, "Fixture author", null).orElseThrow().id);
        });
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    /** A title nothing else in the suite writes, so the shared {@code work} table is clean. */
    private static String unique(String label) {
        return "Medium " + label + " " + UUID.randomUUID();
    }

    private static ManualBookDto entry(Kind kind, String title) {
        return new ManualBookDto(kind, title, "Fixture author", null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }
}
