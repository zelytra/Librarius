package zelytra.librarius.achievement;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.domain.Work;
import zelytra.librarius.web.ApiDtos.AchievementsDto;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Achievements, XP and levels (#287): unlocked by the server, retroactive and silent at first. */
@QuarkusTest
class AchievementApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    @Inject
    AchievementService achievements;

    @Inject
    EntityManager em;

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    /** A reader of its own, so the figures asserted are this test's alone. */
    private String readerWithBooksRead(int books) {
        String id = "achv-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            AppUser user = new AppUser();
            user.id = id;
            em.persist(user);
            for (int i = 0; i < books; i++) {
                Work work = new Work();
                work.kind = Kind.BOOK;
                work.title = "Achv " + id + " " + i;
                em.persist(work);
                Edition edition = new Edition();
                edition.work = work;
                edition.pageCount = 100;
                em.persist(edition);
                LibraryItem item = new LibraryItem();
                item.userId = id;
                item.edition = edition;
                item.status = LibraryStatus.READ;
                em.persist(item);
            }
        });
        return id;
    }

    private static boolean has(AchievementsDto dto, String code, int tier) {
        return dto.achievements().stream().anyMatch(a -> a.code().equals(code) && a.tier() >= tier);
    }

    @Test
    void theFirstEvaluationUnlocksWhatWasEarnedWithoutAnnouncingIt() {
        String reader = readerWithBooksRead(10);

        AchievementsDto first = achievements.overview(reader);
        assertTrue(has(first, "FIRST_BOOK", 1));
        assertTrue(has(first, "BOOKWORM", 1));
        assertTrue(has(first, "PAGE_EATER", 1));
        assertEquals(List.of(), first.unseen());
        // 10 books × 10 XP + FIRST_BOOK 20 + BOOKWORM 25 + PAGE_EATER 25.
        assertEquals(170, first.xp());
        assertEquals(Levels.levelOf(170), first.level());
    }

    @Test
    void anUnlockAfterTheFirstEvaluationIsAnnouncedUntilSeen() {
        String reader = readerWithBooksRead(0);
        assertEquals(List.of(), achievements.overview(reader).unseen());

        achievements.unlock(reader, Achievement.COMEBACK);
        AchievementsDto after = achievements.overview(reader);
        assertEquals(List.of("COMEBACK"), after.unseen().stream().map(u -> u.code()).toList());

        achievements.markSeen(reader);
        assertEquals(List.of(), achievements.overview(reader).unseen());
    }

    /** Reading an abandoned title to the end is the comeback, through a resumed reading too. */
    @Test
    void finishingAnAbandonedTitleIsAComeback() {
        String id = given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"book\": { \"kind\": \"BOOK\", \"title\": \"Comeback " + UUID.randomUUID() + "\" }, \"status\": \"READING\" }")
                .when().post("/api/library")
                .then().statusCode(201).extract().path("id");
        for (String status : new String[] {"ABANDONED", "READING", "READ"}) {
            given().auth().oauth2(token("alice")).contentType("application/json")
                    .body("{ \"status\": \"" + status + "\" }")
                    .when().put("/api/library/" + id + "/progress")
                    .then().statusCode(204);
        }

        given().auth().oauth2(token("alice")).when().get("/api/achievements")
                .then().statusCode(200)
                .body("achievements", hasSize(Achievement.values().length))
                .body("level", greaterThanOrEqualTo(1))
                .body("achievements.find { it.code == 'COMEBACK' }.tier", org.hamcrest.Matchers.is(1));
    }

    @Test
    void oneReadersUnlocksAreTheirsAlone() {
        given().auth().oauth2(token("alice")).when().post("/api/achievements/seen").then().statusCode(204);
        String alice = given().auth().oauth2(token("alice")).when().get("/api/me").then().extract().path("id");
        achievements.unlock(alice, Achievement.COMEBACK);
        // Alice's unseen unlock never reaches Bob's progression.
        given().auth().oauth2(token("bob")).when().get("/api/achievements")
                .then().statusCode(200)
                .body("unseen.code", not(hasItem("COMEBACK")));
    }

    @Test
    void anAnonymousCallIsRefused() {
        given().when().get("/api/achievements").then().statusCode(401);
    }
}
