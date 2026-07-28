package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Verrouille l'isolation des données entre utilisateurs.
 *
 * <p>Il n'y a pas de RLS PostgreSQL : la seule barrière est le filtrage applicatif sur
 * {@code user_id} dans les repositories. Une requête qui oublierait ce filtre exposerait
 * la bibliothèque d'un autre utilisateur sans qu'aucun autre test ne le détecte. Chaque
 * ressource scopée est donc éprouvée ici avec deux comptes distincts.
 *
 * <p>Les assertions sont volontairement relatives (« l'identifiant d'Alice n'apparaît pas
 * chez Bob ») et non absolues : la base est partagée par toute la suite de tests.
 */
@QuarkusTest
class DataIsolationTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String token(String user) {
        return keycloak.getAccessToken(user);
    }

    /** Ajoute un titre à la collection de l'utilisateur et renvoie son identifiant. */
    private String addLibraryItem(String user, String title, String status) {
        return given().auth().oauth2(token(user)).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "%s", "authors": "Isolation Test" },
                          "status": "%s" }
                        """.formatted(title, status))
                .when().post("/api/library")
                .then().statusCode(201)
                .extract().path("id");
    }

    /** Ajoute un souhait à l'utilisateur et renvoie son identifiant. */
    private String addWishlistItem(String user, String title) {
        return given().auth().oauth2(token(user)).contentType("application/json")
                .body("""
                        { "book": { "kind": "MANGA", "title": "%s", "authors": "Isolation Test" },
                          "priority": "SOON" }
                        """.formatted(title))
                .when().post("/api/wishlist")
                .then().statusCode(201)
                .extract().path("id");
    }

    // ── Absence de jeton ──────────────────────────────────────────────────────

    @Test
    void everyScopedResourceRejectsAnonymousAccess() {
        for (String path : new String[] {
                "/api/me", "/api/library", "/api/wishlist", "/api/categories",
                "/api/goals", "/api/stats", "/api/catalog/search?q=test" }) {
            given().when().get(path)
                    .then().statusCode(401);
        }
    }

    // ── Collection ────────────────────────────────────────────────────────────

    @Test
    void libraryItemsAreInvisibleToOtherUsers() {
        String aliceItem = addLibraryItem("alice", "Isolation — collection", "OWNED");

        given().auth().oauth2(token("alice"))
                .when().get("/api/library")
                .then().statusCode(200)
                .body("id", hasItem(aliceItem));

        given().auth().oauth2(token("bob"))
                .when().get("/api/library")
                .then().statusCode(200)
                .body("id", not(hasItem(aliceItem)));
    }

    /**
     * L'accès à l'identifiant d'autrui doit répondre 404 et non 403 : un 403 confirmerait
     * l'existence de la ressource, ce qui est déjà une fuite d'information.
     */
    @Test
    void libraryItemOfAnotherUserCannotBeDeleted() {
        String aliceItem = addLibraryItem("alice", "Isolation — suppression", "OWNED");

        given().auth().oauth2(token("bob"))
                .when().delete("/api/library/" + aliceItem)
                .then().statusCode(404);

        // L'item d'Alice est toujours là.
        given().auth().oauth2(token("alice"))
                .when().get("/api/library")
                .then().statusCode(200)
                .body("id", hasItem(aliceItem));
    }

    @Test
    void libraryItemOfAnotherUserCannotBeRanked() {
        String aliceItem = addLibraryItem("alice", "Isolation — rang", "OWNED");
        String orId = given().auth().oauth2(token("bob")).when().get("/api/categories")
                .then().statusCode(200)
                .extract().path("find { it.code == 'or' }.id");

        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("{ \"categoryId\": \"" + orId + "\" }")
                .when().put("/api/library/" + aliceItem + "/rank")
                .then().statusCode(404);
    }

    @Test
    void libraryItemOfAnotherUserProgressCannotBeUpdated() {
        String aliceItem = addLibraryItem("alice", "Isolation — progression", "READING");

        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("{ \"currentPage\": 999, \"percent\": 99, \"status\": \"READ\" }")
                .when().put("/api/library/" + aliceItem + "/progress")
                .then().statusCode(404);
    }

    // ── Souhaits ──────────────────────────────────────────────────────────────

    @Test
    void wishlistIsInvisibleToOtherUsers() {
        String aliceWish = addWishlistItem("alice", "Isolation — souhait");

        given().auth().oauth2(token("alice"))
                .when().get("/api/wishlist")
                .then().statusCode(200)
                .body("id", hasItem(aliceWish));

        given().auth().oauth2(token("bob"))
                .when().get("/api/wishlist")
                .then().statusCode(200)
                .body("id", not(hasItem(aliceWish)));
    }

    @Test
    void wishlistItemOfAnotherUserCannotBeDeleted() {
        String aliceWish = addWishlistItem("alice", "Isolation — souhait protégé");

        given().auth().oauth2(token("bob"))
                .when().delete("/api/wishlist/" + aliceWish)
                .then().statusCode(404);

        given().auth().oauth2(token("alice"))
                .when().get("/api/wishlist")
                .then().statusCode(200)
                .body("id", hasItem(aliceWish));
    }

    // ── Catégories ────────────────────────────────────────────────────────────

    /**
     * Les built-ins (Or / Argent / Bronze) portent {@code user_id NULL} et sont donc
     * visibles de tous ; une catégorie créée par un utilisateur ne l'est que pour lui.
     */
    @Test
    void customCategoriesAreNotSharedButBuiltinsAre() {
        String aliceCategory = given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"label\": \"Isolation Alice\", \"color\": \"#123456\" }")
                .when().post("/api/categories")
                .then().statusCode(200)
                .body("builtin", is(false))
                .extract().path("id");

        given().auth().oauth2(token("alice"))
                .when().get("/api/categories")
                .then().statusCode(200)
                .body("id", hasItem(aliceCategory));

        given().auth().oauth2(token("bob"))
                .when().get("/api/categories")
                .then().statusCode(200)
                .body("id", not(hasItem(aliceCategory)))
                // Les built-ins restent partagés.
                .body("code", hasItem("or"));
    }

    /** Bob ne peut pas ranger son propre titre dans une catégorie appartenant à Alice. */
    @Test
    void categoryOfAnotherUserCannotBeAssigned() {
        String aliceCategory = given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"label\": \"Isolation Alice Rang\", \"color\": \"#654321\" }")
                .when().post("/api/categories")
                .then().statusCode(200)
                .extract().path("id");

        String bobItem = addLibraryItem("bob", "Isolation — rang croisé", "OWNED");

        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("{ \"categoryId\": \"" + aliceCategory + "\" }")
                .when().put("/api/library/" + bobItem + "/rank")
                .then().statusCode(400);
    }

    // ── Objectifs ─────────────────────────────────────────────────────────────

    /** Années volontairement lointaines pour ne pas heurter les autres tests. */
    @Test
    void goalsAreIsolatedForTheSameYear() {
        given().auth().oauth2(token("alice")).contentType("application/json")
                .body("{ \"targetCount\": 11, \"unit\": \"BOOKS\" }")
                .when().put("/api/goals/2991")
                .then().statusCode(200).body("targetCount", is(11));

        given().auth().oauth2(token("bob")).contentType("application/json")
                .body("{ \"targetCount\": 22, \"unit\": \"BOOKS\" }")
                .when().put("/api/goals/2991")
                .then().statusCode(200).body("targetCount", is(22));

        // Chacun conserve sa propre cible sur la même année.
        given().auth().oauth2(token("alice"))
                .when().get("/api/goals")
                .then().statusCode(200)
                .body("find { it.year == 2991 }.targetCount", is(11));

        given().auth().oauth2(token("bob"))
                .when().get("/api/goals")
                .then().statusCode(200)
                .body("find { it.year == 2991 }.targetCount", is(22));
    }

    // ── Statistiques ──────────────────────────────────────────────────────────

    /**
     * Les statistiques agrègent la collection de l'utilisateur : l'ajout d'un titre lu
     * par Alice ne doit rien changer aux compteurs de Bob.
     */
    @Test
    void statsOnlyCountOwnItems() {
        int bobReadBefore = given().auth().oauth2(token("bob"))
                .when().get("/api/stats")
                .then().statusCode(200)
                .extract().jsonPath().getInt("read");

        addLibraryItem("alice", "Isolation — statistiques", "READ");

        given().auth().oauth2(token("bob"))
                .when().get("/api/stats")
                .then().statusCode(200)
                .body("read", is(bobReadBefore));
    }
}
