package zelytra.librarius.web;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.Work;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Where an entry came from, and whether it survives being recorded (#184).
 *
 * <p>The reference is asserted on the rows rather than through the API: it is not part of
 * any read projection — no screen displays it — and what has to be locked down is that the
 * {@code work} and the {@code edition} hold it, which is what #197 will read.
 *
 * <p>Each test uses a title of its own. Works are matched on
 * (kind, title, authors, volume) and the catalog is shared by the whole suite, so two tests
 * sharing a title would be writing on the same row — which is precisely what half of these
 * tests are about.
 */
@QuarkusTest
class ProviderReferenceApiTest {

    private static final String AUTHOR = "Provider Ref Test";

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    @Inject
    EntityManager em;

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    /**
     * Adds a title, as Discover does when the user taps a search result: the whole book in
     * one call, provider reference included. A {@code null} pair is what the manual form
     * sends, having none.
     */
    private Response add(String user, String title, String provider, String ref) {
        return given().auth().oauth2(token(user)).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "%s", "authors": "%s",
                                    "provider": %s, "providerRef": %s },
                          "status": "OWNED" }
                        """.formatted(title, AUTHOR, quoted(provider), quoted(ref)))
                .when().post("/api/library")
                .then().statusCode(201)
                .extract().response();
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private Work work(Response added) {
        UUID id = UUID.fromString(added.path("book.workId"));
        return QuarkusTransaction.requiringNew().call(() -> em.find(Work.class, id));
    }

    private Edition edition(Response added) {
        UUID id = UUID.fromString(added.path("book.editionId"));
        return QuarkusTransaction.requiringNew().call(() -> em.find(Edition.class, id));
    }

    // ── Recording ─────────────────────────────────────────────────────────────

    /**
     * The point of the issue: a title picked off a live provider hit stays attached to the
     * record it came from, on both levels. The work is what "the other editions of this
     * title" is asked about; the edition is the copy that very record described.
     */
    @Test
    void anEntryFromAProviderKeepsItsReferenceOnTheWorkAndTheEdition() {
        Response added = add("alice", "Référence - depuis un résultat", "anilist", "105778");

        assertEquals("anilist", work(added).provider);
        assertEquals("105778", work(added).providerRef);
        assertEquals("anilist", edition(added).provider);
        assertEquals("105778", edition(added).providerRef);
    }

    /**
     * And a hand-typed entry records nothing, rather than the {@code "manual"} every entry
     * used to be stamped with — a value that marked catalog hits just the same, and so said
     * nothing about anything.
     */
    @Test
    void aHandTypedEntryRecordsNoProvider() {
        Response added = add("alice", "Référence - saisie à la main", null, null);

        assertNull(work(added).provider);
        assertNull(work(added).providerRef);
        assertNull(edition(added).provider);
        assertNull(edition(added).providerRef);
    }

    /**
     * Half a reference is not a reference. A provider name with nothing to look up under it
     * resolves to nothing, and stored it would both lie to whoever reads {@code provider}
     * and freeze the field against the entry that finally knows the whole pair.
     *
     * <p>This is the Open Library case as it stands: its results carry {@code "openlibrary"}
     * and no reference at all, so a book added from Discover records neither. The fix belongs
     * to the provider, not here.
     */
    @Test
    void aProviderWithNoReferenceStoresNeither() {
        Response added = add("alice", "Référence - moitié de paire", "openlibrary", null);

        assertNull(work(added).provider);
        assertNull(work(added).providerRef);
        assertNull(edition(added).provider);
        assertNull(edition(added).providerRef);
    }

    /** A blank is an absence dressed up, and is treated as one rather than stored. */
    @Test
    void blanksCountAsNoReference() {
        Response added = add("alice", "Référence - blancs", "  ", "   ");

        assertNull(work(added).provider);
        assertNull(edition(added).provider);
    }

    /** The wishlist funnels through the same service, so it records the same thing. */
    @Test
    void aWishKeepsTheReferenceToo() {
        String editionId = given().auth().oauth2(token("alice")).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "Référence - une envie",
                                    "authors": "%s", "provider": "anilist",
                                    "providerRef": "30013" },
                          "priority": "SOON" }
                        """.formatted(AUTHOR))
                .when().post("/api/wishlist")
                .then().statusCode(201)
                .extract().path("book.editionId");

        Edition edition = QuarkusTransaction.requiringNew()
                .call(() -> em.find(Edition.class, UUID.fromString(editionId)));
        assertEquals("anilist", edition.provider);
        assertEquals("30013", edition.providerRef);
        assertEquals("anilist", edition.work.provider);
    }

    // ── A work several entries share ──────────────────────────────────────────

    /**
     * The work is shared by everyone owning the title, so its reference follows the rule its
     * synopsis and its genres already follow: a field nobody had filled is picked up from
     * whoever finally supplies it.
     */
    @Test
    void aLaterEntryFillsTheReferenceTheWorkWasMissing() {
        String title = "Référence - complétée après coup";
        Response typed = add("alice", title, null, null);
        assertNull(work(typed).provider);

        Response fromProvider = add("alice", title, "anilist", "31706");

        assertEquals(work(typed).id, work(fromProvider).id, "one work, two editions");
        assertEquals("anilist", work(typed).provider, "the reference reached the shared work");
        assertNull(edition(typed).provider, "…and not the edition entered without one");
    }

    /**
     * The other direction never happens: the row belongs to everyone owning the title, so a
     * second entry naming another record cannot redirect it — the same reason a thin entry
     * cannot wipe a synopsis somebody else supplied.
     */
    @Test
    void aSecondEntryDoesNotRedirectAWorkThatAlreadyHasAReference() {
        String title = "Référence - déjà pointée";
        Response first = add("alice", title, "anilist", "11111");
        Response second = add("alice", title, "anilist", "22222");

        assertEquals(work(first).id, work(second).id);
        assertEquals("11111", work(second).providerRef, "the work keeps the reference it had");
        // The edition, on the other hand, records what its own entry said: it is a different
        // copy, and may well be the one that record describes.
        assertEquals("22222", edition(second).providerRef);
    }

    /**
     * Two users, one title. The reference lands on the shared catalog row — it names a public
     * record, it is not somebody's data — while everything user-scoped stays put: Bob
     * completing the work does not let him at Alice's copy of it, nor change what she owns.
     */
    @Test
    void anotherUsersEntryCompletesTheWorkWithoutReachingTheirCollection() {
        String title = "Référence - deux lecteurs";
        Response alice = add("alice", title, null, null);
        String aliceItem = alice.path("id");
        String aliceEdition = alice.path("book.editionId");

        Response bob = add("bob", title, "anilist", "44444");

        assertEquals(work(alice).id, work(bob).id, "one shared work");
        assertEquals("44444", work(alice).providerRef, "Bob's reference reached the shared work");
        assertNull(edition(alice).provider, "Alice's own edition is untouched");

        // Alice's ownership row is hers alone, whatever Bob did to the catalog around it.
        given().auth().oauth2(token("bob")).when().get("/api/library/" + aliceItem)
                .then().statusCode(404);
        given().auth().oauth2(token("alice")).when().get("/api/library/" + aliceItem)
                .then().statusCode(200)
                .body("book.editionId", is(aliceEdition));
    }

    // ── Round trip ────────────────────────────────────────────────────────────

    /**
     * An export is a backup, and a backup that quietly drops the reference re-creates the very
     * loss this issue is about. It is the one identifier the export carries, because it names
     * a record in a public catalog rather than a row of this instance.
     */
    @Test
    void theExportCarriesTheReferenceSoARestoreKeepsIt() {
        String title = "Référence - export " + UUID.randomUUID();
        add("alice", title, "anilist", "55555");

        Response export = given().auth().oauth2(token("alice")).queryParam("format", "json")
                .when().get("/api/export")
                .then().statusCode(200)
                .extract().response();

        String found = "collection.find { it.book.title == '" + title + "' }.book.";
        export.then()
                .body(found + "provider", is("anilist"))
                .body(found + "providerRef", is("55555"));
    }
}
