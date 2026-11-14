package zelytra.librarius.opinion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;

/** Other communities' ratings on a title, and an author's encyclopedia opening (#453). */
@QuarkusTest
class ExternalOpinionApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private final ObjectMapper json = new ObjectMapper();

    @InjectMock
    ExternalJson external;

    private String token() {
        return keycloak.getAccessToken("alice");
    }

    private JsonNode node(String text) throws Exception {
        return json.readTree(text);
    }

    private String add(String body) {
        String id = given().auth().oauth2(token()).contentType("application/json").body(body)
                .when().post("/api/library").then().statusCode(201).extract().path("id");
        return given().auth().oauth2(token()).when().get("/api/library/" + id)
                .then().extract().path("book.workId");
    }

    @Test
    void openLibraryReadersAreMatchedByIsbnAndCredited() throws Exception {
        Mockito.when(external.get(anyString())).thenReturn(Optional.empty());
        Mockito.when(external.get(contains("isbn%3A9782070000012"))).thenReturn(Optional.of(node("""
                {"docs":[{"key":"/works/OL1W","ratings_average":4.2176,"ratings_count":1080,
                  "already_read_count":1768,"want_to_read_count":21220}]}""")));
        String work = add("""
                { "book": { "kind": "BOOK", "title": "External %s", "isbn13": "9782070000012" } }
                """.formatted(UUID.randomUUID()));

        given().auth().oauth2(token()).when().get("/api/works/" + work + "/external")
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].source", is("Open Library"))
                .body("[0].average", is(4.2f))
                .body("[0].count", is(1080))
                .body("[0].readers", is(1768))
                .body("[0].wanted", is(21220))
                .body("[0].url", is("https://openlibrary.org/works/OL1W"));
    }

    @Test
    void aTitleNoSourceCanMatchHasNothing() {
        Mockito.when(external.get(anyString())).thenReturn(Optional.empty());
        String work = add("""
                { "book": { "kind": "BOOK", "title": "No external %s" } }
                """.formatted(UUID.randomUUID()));
        given().auth().oauth2(token()).when().get("/api/works/" + work + "/external")
                .then().statusCode(200).body("$", empty());
        // No ISBN, no Open Library key: the source is not even asked.
        Mockito.verify(external, Mockito.never()).get(contains("openlibrary"));
    }

    @Test
    void aMangaTakesAniListsScoreOnlyForItsExactTitle() throws Exception {
        Mockito.when(external.get(anyString())).thenReturn(Optional.empty());
        String series = "Ext Manga " + UUID.randomUUID();
        Mockito.when(external.post(anyString(), contains(series))).thenReturn(Optional.of(node("""
                {"data":{"Media":{"id":1,"averageScore":86,"popularity":5000,"siteUrl":"https://anilist.co/manga/1",
                  "title":{"romaji":"%s","english":null,"native":null},"synonyms":[]}}}""".formatted(series))));
        String work = add("""
                { "book": { "kind": "MANGA", "title": "%s Tome 1", "seriesTitle": "%s", "volumeNumber": 1 } }
                """.formatted(series, series));

        given().auth().oauth2(token()).when().get("/api/works/" + work + "/external")
                .then().statusCode(200)
                .body("[0].source", is("AniList"))
                .body("[0].average", is(4.3f));

        JsonNode other = node("""
                {"title":{"romaji":"One Piece Party"},"synonyms":["OPP"]}""");
        assertFalse(ExternalOpinionService.namesTitle(other, "One Piece"));
        assertTrue(ExternalOpinionService.namesTitle(other, "one-piece party"));
    }

    @Test
    void anAuthorGetsTheOpeningOfTheirArticleButNotADisambiguation() throws Exception {
        String name = "Auteur Externe " + UUID.randomUUID().toString().substring(0, 8);
        Mockito.when(external.get(anyString())).thenReturn(Optional.of(node("{\"type\":\"disambiguation\",\"extract\":\"x\"}")));
        Mockito.when(external.get(contains(name.replace(' ', '_')))).thenReturn(Optional.of(node("""
                {"type":"standard","extract":"Écrivain français.","description":"écrivain",
                 "content_urls":{"desktop":{"page":"https://fr.wikipedia.org/wiki/X"}}}""")));
        add("""
                { "book": { "kind": "BOOK", "title": "By external %s", "authors": "%s" } }
                """.formatted(UUID.randomUUID(), name));
        String author = given().auth().oauth2(token()).when().get("/api/authors?q=" + name)
                .then().statusCode(200).extract().path("[0].id");

        given().auth().oauth2(token()).when().get("/api/authors/" + author + "/external")
                .then().statusCode(200)
                .body("[0].source", is("Wikipédia"))
                .body("[0].extract", is("Écrivain français."))
                .body("[0].licence", is("CC BY-SA 4.0"));
    }
}
