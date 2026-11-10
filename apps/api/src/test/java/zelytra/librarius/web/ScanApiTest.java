package zelytra.librarius.web;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import zelytra.librarius.catalog.CatalogQuery;
import zelytra.librarius.catalog.CatalogResult;
import zelytra.librarius.catalog.CatalogService;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/** #68: what a scanned ISBN is to the reader — theirs, wished for, or new to them. */
@QuarkusTest
class ScanApiTest {

    private static final KeycloakTestClient KEYCLOAK = new KeycloakTestClient();

    @InjectMock
    CatalogService catalog;

    /** A valid ISBN-13 no other test uses: the test database is shared by the whole suite. */
    private static String freshIsbn() {
        StringBuilder twelve = new StringBuilder("979");
        for (int i = 0; i < 9; i++) {
            twelve.append(ThreadLocalRandom.current().nextInt(10));
        }
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += (twelve.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return twelve.append((10 - sum % 10) % 10).toString();
    }

    private static String add(String user, String path, String bookJson, String extra) {
        return given().auth().oauth2(KEYCLOAK.getAccessToken(user)).contentType("application/json")
                .body("{ \"book\": " + bookJson + extra + " }")
                .when().post(path).then().statusCode(201).extract().path("id");
    }

    private static io.restassured.response.ValidatableResponse scan(String user, String isbn) {
        return given().auth().oauth2(KEYCLOAK.getAccessToken(user)).when().get("/api/scan/" + isbn).then();
    }

    private void catalogKnows(String isbn, CatalogResult result) {
        Mockito.when(catalog.search(ArgumentMatchers.eq(Set.of()),
                ArgumentMatchers.eq(new CatalogQuery(null, null, null, null, null, isbn)),
                ArgumentMatchers.anyInt())).thenReturn(List.of(result));
    }

    private static CatalogResult book(String title, String authors, String isbn, String series, Integer volume) {
        return new CatalogResult("BOOK", title, authors, 2020, null, null, isbn, null, null, null,
                series, volume, null, "openlibrary", "OL1M");
    }

    @Test
    void recognisesABookOwnedUnderThatIsbnWithoutAskingTheCatalogs() {
        String isbn = freshIsbn();
        String id = add("alice", "/api/library",
                "{ \"kind\": \"BOOK\", \"title\": \"Scan owned\", \"authors\": \"Scan Test\", \"isbn13\": \"" + isbn + "\" }",
                ", \"status\": \"READ\"");

        scan("alice", isbn).statusCode(200)
                .body("match", is("ISBN"))
                .body("owned.id", is(id))
                .body("owned.status", is("READ"))
                .body("catalog", nullValue());
        Mockito.verifyNoInteractions(catalog);
    }

    @Test
    void recognisesAnImportedBookWithNoIsbnByItsTitleAndAuthor() {
        // Booknode and Babelio publish no ISBN: the imported copy has none.
        String marker = "Scan Title " + System.nanoTime();
        String id = add("alice", "/api/library",
                "{ \"kind\": \"BOOK\", \"title\": \"" + marker + "\", \"authors\": \"Victor Hugo\" }",
                ", \"status\": \"OWNED\"");
        String isbn = freshIsbn();
        catalogKnows(isbn, book(marker, "Hugo, Victor", isbn, null, null));

        scan("alice", isbn).statusCode(200)
                .body("match", is("TITLE"))
                .body("owned.id", is(id))
                .body("catalog.isbn13", is(isbn));
    }

    @Test
    void recognisesAVolumeOfASeriesByItsNumber() {
        String series = "Scan Saga " + System.nanoTime();
        String id = add("alice", "/api/library",
                "{ \"kind\": \"MANGA\", \"title\": \"" + series + " T3\", \"authors\": \"A. Author\", "
                        + "\"seriesTitle\": \"" + series + "\", \"volumeNumber\": 3 }",
                ", \"status\": \"OWNED\"");
        String isbn = freshIsbn();
        catalogKnows(isbn, book(series + " - Volume 3 : another title", "A. Author", isbn, series, 3));

        scan("alice", isbn).statusCode(200)
                .body("match", is("SERIES_VOLUME"))
                .body("owned.id", is(id));
    }

    @Test
    void findsAWishAndKeepsAnotherReadersBooksOutOfIt() {
        String isbn = freshIsbn();
        String wish = add("alice", "/api/wishlist",
                "{ \"kind\": \"BOOK\", \"title\": \"Scan wish\", \"authors\": \"Scan Test\", \"isbn13\": \"" + isbn + "\" }",
                ", \"priority\": \"SOON\"");

        scan("alice", isbn).statusCode(200).body("match", is("ISBN")).body("wished.id", is(wish))
                .body("owned", nullValue());

        // Bob scanning the same book is told it is new to him, with the catalog entry to add it.
        catalogKnows(isbn, book("Scan wish", "Scan Test", isbn, null, null));
        scan("bob", isbn).statusCode(200)
                .body("match", is("NONE"))
                .body("wished", nullValue())
                .body("catalog.title", is("Scan wish"));
    }

    @Test
    void answersNothingKnownAndRefusesWhatIsNotAnIsbn() {
        String isbn = freshIsbn();
        Mockito.when(catalog.search(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
        scan("alice", isbn).statusCode(200).body("match", is("NONE")).body("catalog", nullValue())
                .body("isbn13", notNullValue());

        scan("alice", "3017620422003").statusCode(400);
    }
}
