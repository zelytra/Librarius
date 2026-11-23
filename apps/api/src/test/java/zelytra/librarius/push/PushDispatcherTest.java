package zelytra.librarius.push;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * #69: the daily run that decides who gets which push. The sender is mocked — the run is what
 * is locked down: who opted in, which day, which device, and that a second run sends nothing.
 */
@QuarkusTest
class PushDispatcherTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    @InjectMock
    FcmSender sender;

    @Inject
    PushDispatcher dispatcher;

    @Inject
    EntityManager em;

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    @BeforeEach
    void deliverEverything() {
        Mockito.when(sender.enabled()).thenReturn(true);
        Mockito.when(sender.send(any(), any())).thenReturn(FcmSender.Outcome.DELIVERED);
        for (String user : List.of("alice", "bob", "carol")) {
            prefs(user, false, false, false);
        }
    }

    private void prefs(String user, boolean releases, boolean wishlist, boolean goal) {
        given().auth().oauth2(token(user)).contentType("application/json")
                .body("{ \"releases\": %s, \"wishlist\": %s, \"goal\": %s }".formatted(releases, wishlist, goal))
                .when().put("/api/me/push").then().statusCode(200);
    }

    private String device(String user) {
        String device = "fcm-" + UUID.randomUUID();
        given().auth().oauth2(token(user)).contentType("application/json")
                .body("{ \"token\": \"%s\", \"platform\": \"android\" }".formatted(device))
                .when().put("/api/me/push/devices").then().statusCode(204);
        return device;
    }

    /** Alice owns volume 1 of a new run; returns its id. */
    private UUID seedRun(String title) {
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("""
                        { "book": { "kind": "MANGA", "title": "%s vol. 1", "authors": "%s Author",
                                    "seriesTitle": "%s", "volumeNumber": 1 },
                          "status": "TO_READ" }
                        """.formatted(title, title, title))
                .when().post("/api/library").then().statusCode(201);
        String id = given().auth().oauth2(token("alice")).when().get("/api/series").then()
                .extract().path("find { it.title == '" + title + "' }.id");
        return UUID.fromString(id);
    }

    private void announce(UUID series, int volume, LocalDate date) {
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"seriesId\": \"%s\", \"volumeNumber\": %d, \"releaseDate\": \"%s\" }"
                        .formatted(series, volume, date))
                .when().post("/api/admin/releases").then().statusCode(201);
    }

    private List<PushMessage> sentTo(String device) {
        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        Mockito.verify(sender, Mockito.atLeast(0)).send(eq(device), captor.capture());
        return captor.getAllValues();
    }

    @Test
    void aVolumeOfAnOwnedRunIsAnnouncedAWeekAheadAndOnTheDay() {
        LocalDate today = LocalDate.of(2031, 3, 10);
        UUID run = seedRun("Push semaine " + UUID.randomUUID());
        announce(run, 2, today.plusDays(7));
        prefs("alice", true, false, false);
        String phone = device("alice");

        dispatcher.run(today);
        dispatcher.run(today);

        List<PushMessage> sent = sentTo(phone);
        assertEquals(1, sent.size(), "a second run on the same day sends nothing again");
        assertEquals("/series/" + run, sent.get(0).path());
        assertTrue(sent.get(0).body().contains("7"), sent.get(0).body());

        dispatcher.run(today.plusDays(7));
        assertEquals(2, sentTo(phone).size(), "and once more on the release day");
    }

    @Test
    void nothingIsSentWithoutTheOptIn() {
        LocalDate today = LocalDate.of(2031, 4, 10);
        UUID run = seedRun("Push sans accord " + UUID.randomUUID());
        announce(run, 2, today);
        String phone = device("alice");

        dispatcher.run(today);

        assertEquals(List.of(), sentTo(phone));
    }

    @Test
    void aReaderWithNoStakeInTheRunHearsNothing() {
        LocalDate today = LocalDate.of(2031, 5, 10);
        UUID run = seedRun("Push étranger " + UUID.randomUUID());
        announce(run, 2, today);
        prefs("bob", true, true, false);
        String phone = device("bob");

        dispatcher.run(today);

        assertEquals(List.of(), sentTo(phone));
    }

    @Test
    void aPriorityWishReleasedTodayIsAnnouncedAsAWish() {
        LocalDate today = LocalDate.of(2031, 6, 10);
        String title = "Push souhait " + UUID.randomUUID();
        UUID run = seedRun(title);
        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("""
                        { "book": { "kind": "MANGA", "title": "%s vol. 2", "authors": "%s Author",
                                    "seriesTitle": "%s", "volumeNumber": 2 },
                          "priority": "PRIORITY" }
                        """.formatted(title, title, title))
                .when().post("/api/wishlist").then().statusCode(201);
        announce(run, 2, today);
        prefs("bob", false, true, false);
        String phone = device("bob");

        dispatcher.run(today);

        List<PushMessage> sent = sentTo(phone);
        assertEquals(1, sent.size());
        assertEquals("/wishlist", sent.get(0).path());
    }

    @Test
    void theGoalReminderComesOnTheFirstOfTheMonthOnly() {
        given().auth().oauth2(token("carol")).contentType("application/json")
                .body("{ \"targetCount\": 24 }")
                .when().put("/api/goals/2031").then().statusCode(200);
        prefs("carol", false, false, true);
        String phone = device("carol");

        dispatcher.run(LocalDate.of(2031, 7, 2));
        assertEquals(List.of(), sentTo(phone));

        dispatcher.run(LocalDate.of(2031, 7, 1));
        List<PushMessage> sent = sentTo(phone);
        assertEquals(1, sent.size());
        assertEquals("/stats", sent.get(0).path());
    }

    @Test
    void aDeviceFcmNoLongerKnowsIsForgotten() {
        LocalDate today = LocalDate.of(2031, 8, 1);
        given().auth().oauth2(token("carol")).contentType("application/json")
                .body("{ \"targetCount\": 12 }")
                .when().put("/api/goals/2031").then().statusCode(200);
        prefs("carol", false, false, true);
        String phone = device("carol");
        Mockito.when(sender.send(eq(phone), any())).thenReturn(FcmSender.Outcome.UNREGISTERED);

        dispatcher.run(today);

        long left = QuarkusTransaction.requiringNew().call(() -> ((Number) em
                .createNativeQuery("select count(*) from push_device where token = :token")
                .setParameter("token", phone).getSingleResult()).longValue());
        assertEquals(0, left);
    }

    @Test
    void anUnconfiguredSenderMakesTheRunANoOp() {
        Mockito.when(sender.enabled()).thenReturn(false);
        given().auth().oauth2(token("carol")).contentType("application/json")
                .body("{ \"targetCount\": 6 }")
                .when().put("/api/goals/2031").then().statusCode(200);
        prefs("carol", false, false, true);
        String phone = device("carol");

        dispatcher.run(LocalDate.of(2031, 9, 1));

        assertEquals(List.of(), sentTo(phone));
    }
}
