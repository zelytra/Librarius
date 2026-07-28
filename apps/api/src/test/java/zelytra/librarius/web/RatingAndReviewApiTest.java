package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * Personal rating and private review, and the two things the collection can do with a
 * rating: keep only the favourites, and order on it.
 *
 * <p>The database is shared by the whole suite, so a test asserting on a listing gives its
 * fixtures an author of its own and narrows the collection down to it through {@code q}.
 */
@QuarkusTest
class RatingAndReviewApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String token() {
        return keycloak.getAccessToken("alice");
    }

    /** Adds a read title by {@code author} and returns its identifier. */
    private String addBook(String title, String author) {
        return addBook(title, author, null);
    }

    /** Same, carrying a genre — for the tests that combine the two filters. */
    private String addBook(String title, String author, String genres) {
        return given().auth().oauth2(token()).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "%s", "authors": "%s",
                                    "genres": %s },
                          "status": "READ" }
                        """.formatted(title, author,
                        genres == null ? "null" : "\"" + genres + "\""))
                .when().post("/api/library")
                .then().statusCode(201)
                .extract().path("id");
    }

    private ValidatableResponse putReview(String itemId, String body) {
        return given().auth().oauth2(token()).contentType("application/json").body(body)
                .when().put("/api/library/" + itemId + "/review")
                .then();
    }

    /** The titles the collection returns for a given author, in the order it returns them. */
    private ValidatableResponse listByAuthor(String author, Object... queryParams) {
        var request = given().auth().oauth2(token()).queryParam("q", author);
        for (int i = 0; i < queryParams.length; i += 2) {
            request = request.queryParam((String) queryParams[i], queryParams[i + 1]);
        }
        return request.when().get("/api/library").then();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Test
    void aRatingAndAReviewAreReadBackAgain() {
        String itemId = addBook("Rating - persisted", "Rating Persisted");

        putReview(itemId, "{ \"rating\": 5, \"review\": \"Relu trois fois.\" }")
                .statusCode(200)
                .body("rating", is(5))
                .body("review", is("Relu trois fois."));

        given().auth().oauth2(token())
                .when().get("/api/library/" + itemId)
                .then().statusCode(200)
                .body("rating", is(5))
                .body("review", is("Relu trois fois."));
    }

    /** An empty text area is the absence of a review, not a row holding an empty string. */
    @Test
    void aBlankReviewIsStoredAsNothing() {
        String itemId = addBook("Rating - blank review", "Rating Blank");

        putReview(itemId, "{ \"rating\": 3, \"review\": \"   \" }")
                .statusCode(200)
                .body("review", nullValue());
    }

    /** Un-rating a title has to be possible: the PUT replaces the pair as a whole. */
    @Test
    void theRatingCanBeCleared() {
        String itemId = addBook("Rating - cleared", "Rating Cleared");
        putReview(itemId, "{ \"rating\": 4 }").statusCode(200).body("rating", is(4));

        putReview(itemId, "{}").statusCode(200).body("rating", nullValue());
    }

    @Test
    void anOutOfRangeRatingIsRejected() {
        String itemId = addBook("Rating - out of range", "Rating Range");

        putReview(itemId, "{ \"rating\": 0 }").statusCode(400);
        putReview(itemId, "{ \"rating\": 6 }").statusCode(400);
    }

    @Test
    void anUnknownItemAnswersNotFound() {
        putReview("00000000-0000-0000-0000-0000000000ff", "{ \"rating\": 4 }").statusCode(404);
    }

    // ── Collection ────────────────────────────────────────────────────────────

    @Test
    void theFavouritesFilterKeepsTheTitlesRatedFourOrMore() {
        String author = "Rating Favourites";
        putReview(addBook("Favourite - five", author), "{ \"rating\": 5 }").statusCode(200);
        putReview(addBook("Favourite - four", author), "{ \"rating\": 4 }").statusCode(200);
        putReview(addBook("Favourite - two", author), "{ \"rating\": 2 }").statusCode(200);
        addBook("Favourite - unrated", author);

        listByAuthor(author, "minRating", 4, "sort", "rating")
                .statusCode(200)
                .body("total", is(2))
                .body("items.book.title", contains("Favourite - five", "Favourite - four"));
    }

    /** Unrated titles land after the rated ones: unjudged is not the same as judged badly. */
    @Test
    void orderingByRatingPutsTheUnratedLast() {
        String author = "Rating Ordering";
        String rated = addBook("Ordering - rated", author);
        addBook("Ordering - unrated", author);
        putReview(rated, "{ \"rating\": 1 }").statusCode(200);

        listByAuthor(author, "sort", "rating")
                .statusCode(200)
                .body("items.book.title", contains("Ordering - rated", "Ordering - unrated"));
    }

    /**
     * The rating filter narrows what the genre filter left, rather than replacing it.
     *
     * <p>They arrived in two changes that crossed on their way to {@code main} and were
     * merged by hand into the same {@code where}; nothing else would notice one of them
     * quietly dropping the other.
     */
    @Test
    void theGenreAndRatingFiltersCombine() {
        String author = "Rating And Genre";
        putReview(addBook("Combined - fantasy loved", author, "Fantasy"), "{ \"rating\": 5 }")
                .statusCode(200);
        putReview(addBook("Combined - fantasy meh", author, "Fantasy"), "{ \"rating\": 2 }")
                .statusCode(200);
        putReview(addBook("Combined - policier loved", author, "Policier"), "{ \"rating\": 5 }")
                .statusCode(200);

        listByAuthor(author, "genre", "fantasy", "minRating", 4)
                .statusCode(200)
                .body("total", is(1))
                .body("items[0].book.title", is("Combined - fantasy loved"));

        // Each criterion on its own still selects what it selected before.
        listByAuthor(author, "genre", "fantasy").statusCode(200).body("total", is(2));
        listByAuthor(author, "minRating", 4).statusCode(200).body("total", is(2));
    }

    @Test
    void anOutOfRangeMinRatingIsRejected() {
        given().auth().oauth2(token()).queryParam("minRating", 9)
                .when().get("/api/library")
                .then().statusCode(400);
    }
}
