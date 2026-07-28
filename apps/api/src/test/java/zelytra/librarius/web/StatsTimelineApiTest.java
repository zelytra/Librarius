package zelytra.librarius.web;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.LibraryItem;
import zelytra.librarius.domain.LibraryStatus;
import zelytra.librarius.domain.ReadingProgress;
import zelytra.librarius.domain.Work;

import java.time.LocalDate;
import java.time.Year;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The time-based statistics, end to end.
 *
 * <p>Each test works in a calendar year of its own, well before the current one: the
 * database is shared by the whole suite, and a title another test marks as read is stamped
 * with today's date. A window in 2016 or 2019 belongs to this class alone, so the figures
 * can be asserted absolutely rather than as a delta.
 */
@QuarkusTest
class StatsTimelineApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    @Inject
    EntityManager em;

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    /** The caller's identifier, {@code /api/me} creating the {@code app_user} row on the fly. */
    private String userId(String user) {
        return given().auth().oauth2(token(user))
                .when().get("/api/me")
                .then().statusCode(200)
                .extract().path("id");
    }

    // ── Buckets ───────────────────────────────────────────────────────────────

    @Test
    void chartsEveryMonthTheUserFinishedSomethingIn() {
        String alice = userId("alice");
        seed(alice, LocalDate.of(2019, 1, 5), LocalDate.of(2019, 1, 1), 100, "Patrick Rothfuss");
        seed(alice, LocalDate.of(2019, 1, 20), null, 200, "Patrick Rothfuss");
        seed(alice, LocalDate.of(2019, 3, 15), LocalDate.of(2019, 3, 5), 150, "Frank Herbert");

        float daysPerBook = given().auth().oauth2(token("alice"))
                .queryParam("from", "2019-01-01").queryParam("to", "2019-12-31")
                .when().get("/api/stats/timeline")
                .then().statusCode(200)
                .body("granularity", is("MONTH"))
                .body("points.period", contains("2019-01", "2019-03"))
                .body("points.books", contains(2, 1))
                .body("points.pages", contains(300, 150))
                .body("books", is(3))
                .body("pages", is(450))
                .body("bestPeriod", is("2019-01"))
                .body("bestPeriodBooks", is(2))
                .body("byAuthor[0].label", is("Patrick Rothfuss"))
                .body("byAuthor[0].count", is(2))
                .extract().jsonPath().getFloat("daysPerBook");

        // Two of the three titles carry both dates: four days and ten days.
        assertEquals(7.0, daysPerBook, 0.001);
    }

    @Test
    void groupsByYearWhenAskedTo() {
        String alice = userId("alice");
        seed(alice, LocalDate.of(2016, 4, 2), null, 80, "Isolation Timeline");
        seed(alice, LocalDate.of(2017, 9, 9), null, 120, "Isolation Timeline");

        given().auth().oauth2(token("alice"))
                .queryParam("from", "2016-01-01").queryParam("to", "2017-12-31")
                .queryParam("granularity", "year")
                .when().get("/api/stats/timeline")
                .then().statusCode(200)
                .body("granularity", is("YEAR"))
                .body("points.period", contains("2016", "2017"))
                .body("points.pages", contains(80, 120));
    }

    /** A window nobody read in answers an empty timeline, not a 404 and not a row of zeros. */
    @Test
    void answersAnEmptyTimelineForAWindowWithoutReading() {
        given().auth().oauth2(token("alice"))
                .queryParam("from", "2005-01-01").queryParam("to", "2005-12-31")
                .when().get("/api/stats/timeline")
                .then().statusCode(200)
                .body("points.size()", is(0))
                .body("books", is(0))
                .body("pages", is(0))
                .body("bestPeriod", nullValue())
                .body("daysPerBook", nullValue());
    }

    // ── Window ────────────────────────────────────────────────────────────────

    /** No parameters means the year in progress, which is what the goal is about. */
    @Test
    void defaultsToTheCurrentYear() {
        int year = Year.now().getValue();

        given().auth().oauth2(token("alice"))
                .when().get("/api/stats/timeline")
                .then().statusCode(200)
                .body("from", is(year + "-01-01"))
                .body("to", is(year + "-12-31"))
                .body("granularity", is("MONTH"))
                .body("pagesPerDay", notNullValue());
    }

    @Test
    void rejectsAWindowItCannotMakeSenseOf() {
        given().auth().oauth2(token("alice")).queryParam("from", "hier")
                .when().get("/api/stats/timeline")
                .then().statusCode(400);

        given().auth().oauth2(token("alice"))
                .queryParam("from", "2019-12-31").queryParam("to", "2019-01-01")
                .when().get("/api/stats/timeline")
                .then().statusCode(400);

        given().auth().oauth2(token("alice")).queryParam("granularity", "decade")
                .when().get("/api/stats/timeline")
                .then().statusCode(400);
    }

    // ── Goal progress ─────────────────────────────────────────────────────────

    /**
     * The timeline is fed by the application itself, not only by the fixture above: marking
     * a title as read stamps {@code finished_at} with today's date, so it lands in today's
     * bucket of the default window.
     */
    @Test
    void marksATitleAsReadIntoThisMonthsBucket() {
        String item = given().auth().oauth2(token("alice")).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "Timeline - finished today",
                                    "authors": "Timeline Test", "pageCount": 321 },
                          "status": "OWNED" }
                        """)
                .when().post("/api/library")
                .then().statusCode(201)
                .extract().path("id");

        String bucket = "%04d-%02d".formatted(Year.now().getValue(), LocalDate.now().getMonthValue());
        int before = booksIn(bucket);

        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"percent\": 100, \"status\": \"READ\" }")
                .when().put("/api/library/" + item + "/progress")
                .then().statusCode(204);

        assertEquals(before + 1, booksIn(bucket), "today's month holds the title just finished");

        // Going back to READING clears the finishing date: the title leaves the bucket.
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"percent\": 20, \"status\": \"READING\" }")
                .when().put("/api/library/" + item + "/progress")
                .then().statusCode(204);

        assertEquals(before, booksIn(bucket), "a title being read again is not a finished one");
    }

    /** Titles the default window reports for one bucket; zero when the bucket is absent. */
    private int booksIn(String bucket) {
        Integer books = given().auth().oauth2(token("alice"))
                .when().get("/api/stats/timeline")
                .then().statusCode(200)
                .extract().jsonPath()
                .get("points.find { it.period == '" + bucket + "' }.books");
        return books == null ? 0 : books;
    }

    // ── Fixture ───────────────────────────────────────────────────────────────

    /**
     * Inserts a title the user finished on a given day. Written straight to the database
     * rather than through the API: {@code PUT /progress} stamps today, and these tests need
     * the reading to sit in a year of their own.
     */
    private void seed(String userId, LocalDate finishedAt, LocalDate startedAt, Integer pageCount,
            String authors) {
        QuarkusTransaction.requiringNew().run(() -> {
            Work work = new Work();
            work.kind = Kind.BOOK;
            work.title = "Timeline " + UUID.randomUUID();
            work.authors = authors;
            em.persist(work);

            Edition edition = new Edition();
            edition.work = work;
            edition.pageCount = pageCount;
            em.persist(edition);

            LibraryItem item = new LibraryItem();
            item.userId = userId;
            item.edition = edition;
            item.status = LibraryStatus.READ;
            em.persist(item);

            ReadingProgress progress = new ReadingProgress();
            progress.libraryItem = item;
            progress.startedAt = startedAt;
            progress.finishedAt = finishedAt;
            em.persist(progress);
        });
    }
}
