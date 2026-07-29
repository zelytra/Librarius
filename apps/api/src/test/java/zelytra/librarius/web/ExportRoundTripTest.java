package zelytra.librarius.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zelytra.librarius.account.RecordingKeycloakAccountDeleter;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The round trip of #72: export, wipe, re-import, and get the same library back.
 *
 * <p>Asserted by comparing the two documents rather than by walking the schema field by
 * field — a per-field check only ever covers the fields somebody remembered to list, and the
 * one that gets forgotten is the one that gets lost. Both exports are ordered
 * deterministically (see {@code ExportService}), so the only value that may legitimately
 * differ between them is {@code exportedAt}.
 *
 * <p>Runs as <b>carol</b>, and wipes with {@code DELETE /api/me}: it is the only way to empty
 * an account completely, goals and categories included, and it is also the flow the two
 * issues describe — export, then delete.
 */
@QuarkusTest
class ExportRoundTripTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    RecordingKeycloakAccountDeleter identityProvider;

    private String token() {
        return keycloak.getAccessToken("carol");
    }

    @BeforeEach
    void cleanSlate() {
        identityProvider.reset();
        given().auth().oauth2(token()).when().delete("/api/me").then().statusCode(200);
    }

    /** One of everything, with accents and punctuation the serialisers could mangle. */
    private void fillAccount() {
        given().auth().oauth2(token()).contentType("application/json")
                .body("""
                        { "book": { "kind": "MANGA", "title": "L'Été où j'ai grandi",
                                    "authors": "Taiyō Matsumoto", "seriesTitle": "Été",
                                    "volumeNumber": 1, "isbn13": "9782505012345",
                                    "publisher": "Kana", "language": "fr", "pageCount": 224,
                                    "coverUrl": "https://example.test/cover.jpg",
                                    "format": "Poche", "releaseDate": "2019-04-11",
                                    "originalYear": 2003, "synopsis": "Un été ; deux frères.",
                                    "genres": "Seinen, Tranche de vie" },
                          "status": "READING", "rating": 5, "acquiredAt": "2026-02-14" }
                        """)
                .when().post("/api/library").then().statusCode(201);

        given().auth().oauth2(token()).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "Ravage",
                                    "authors": "René Barjavel", "genres": "Science-Fiction",
                                    "isbn13": "9782070366231", "publisher": "Gallimard" },
                          "status": "READ", "rating": 4 }
                        """)
                .when().post("/api/library").then().statusCode(201);

        // The same work in a second edition (#152). A restore keyed on the title alone
        // would bring one of the two back and drop the other without saying so.
        given().auth().oauth2(token()).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "Ravage",
                                    "authors": "René Barjavel", "genres": "Science-Fiction",
                                    "isbn13": "9782070612079", "publisher": "Folio",
                                    "format": "Poche", "pageCount": 320 },
                          "status": "OWNED" }
                        """)
                .when().post("/api/library").then().statusCode(201);

        String reading = given().auth().oauth2(token()).queryParam("q", "Été")
                .when().get("/api/library").then().statusCode(200)
                .extract().path("items[0].id");

        given().auth().oauth2(token()).contentType("application/json")
                .body("""
                        { "currentPage": 88, "percent": 39, "status": "READING",
                          "startedAt": "2026-03-01" }
                        """)
                .when().put("/api/library/" + reading + "/progress").then().statusCode(204);

        // The private review (#140) is the most personal thing the account holds; an export
        // that dropped it would look complete and would not be.
        given().auth().oauth2(token()).contentType("application/json")
                .body("""
                        { "rating": 5,
                          "review": "Lu d'une traite ; le dessin fait tout le travail." }
                        """)
                .when().put("/api/library/" + reading + "/review").then().statusCode(200);

        String category = given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"label\": \"Coup de cœur\", \"color\": \"#c25b6a\" }")
                .when().post("/api/categories").then().statusCode(200).extract().path("id");

        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"categoryId\": \"" + category + "\" }")
                .when().put("/api/library/" + reading + "/rank").then().statusCode(200);

        given().auth().oauth2(token()).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "La Horde du Contrevent",
                                    "authors": "Alain Damasio" },
                          "priority": "PRIORITY", "estimatedPrice": 24.50,
                          "note": "édition brochée ; pas le poche" }
                        """)
                .when().post("/api/wishlist").then().statusCode(201);

        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"targetCount\": 52, \"unit\": \"BOOKS\" }")
                .when().put("/api/goals/2989").then().statusCode(200);

        String series = given().auth().oauth2(token()).when().get("/api/series")
                .then().statusCode(200).extract().path("find { it.title == 'Été' }.id");
        given().auth().oauth2(token()).when().put("/api/series/" + series + "/follow")
                .then().statusCode(204);
    }

    private String exportJson() {
        return given().auth().oauth2(token()).queryParam("format", "json")
                .when().get("/api/export")
                .then().statusCode(200)
                .extract().asString();
    }

    @Test
    void theJsonExportRestoresAnIdenticalLibrary() throws Exception {
        fillAccount();
        String before = exportJson();

        ObjectNode expected = (ObjectNode) mapper.readTree(before);
        // The document has to be worth comparing: a round trip between two empty libraries
        // would pass whatever the import does. Three titles, two of which are the same work
        // in two editions.
        assertEquals(3, expected.get("collection").size());
        assertEquals(1, expected.get("wishlist").size());
        assertEquals(1, expected.get("goals").size());
        assertEquals(1, expected.get("categories").size());
        assertEquals(1, expected.get("followedSeries").size());
        assertTrue(before.contains("le dessin fait tout le travail"),
                "the private review must be part of the archive");

        given().auth().oauth2(token()).when().delete("/api/me").then().statusCode(200);
        given().auth().oauth2(token()).when().get("/api/library")
                .then().statusCode(200).body("total", is(0));

        given().auth().oauth2(token()).contentType("application/json").body(before)
                .when().post("/api/import/json")
                .then().statusCode(200)
                .body("source", is("json"))
                // Three owned titles and one wish; the two editions of Ravage count as two.
                .body("imported", is(4))
                .body("skipped", is(0));

        ObjectNode actual = (ObjectNode) mapper.readTree(exportJson());
        assertNotEquals(expected.get("exportedAt"), actual.get("exportedAt"),
                "the two exports were taken at the same instant; the comparison below would "
                        + "not prove that the field is the only difference");
        expected.remove("exportedAt");
        actual.remove("exportedAt");
        assertEquals(expected, actual, "re-importing the export must restore the same library");
    }

    /** Importing the same document twice adds nothing: the restore is additive, not blind. */
    @Test
    void reimportingTheSameDocumentTwiceChangesNothing() throws Exception {
        fillAccount();
        String archive = exportJson();

        given().auth().oauth2(token()).contentType("application/json").body(archive)
                .when().post("/api/import/json")
                .then().statusCode(200)
                .body("imported", is(0))
                .body("skipped", is(4));

        ObjectNode after = (ObjectNode) mapper.readTree(exportJson());
        assertEquals(3, after.get("collection").size());
        assertEquals(1, after.get("wishlist").size());
        assertEquals(1, after.get("categories").size());
    }

    /** A document from a newer version is refused rather than half-read. */
    @Test
    void aDocumentFromANewerVersionIsRejected() {
        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"schemaVersion\": 99, \"collection\": [] }")
                .when().post("/api/import/json")
                .then().statusCode(400)
                .body("message", org.hamcrest.Matchers.containsString("version plus récente"));
    }
}
