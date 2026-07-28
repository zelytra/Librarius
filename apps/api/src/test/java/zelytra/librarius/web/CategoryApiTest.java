package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * Custom ranking categories: creation, renaming, deletion, and what each of them does to the
 * titles filed under a category.
 *
 * <p>The database is shared by the whole suite, so every test uses labels of its own: a
 * category name is unique per user, and reusing one across tests would make the second
 * creation answer 409 for reasons that have nothing to do with what is being tested.
 */
@QuarkusTest
class CategoryApiTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    private String token() {
        return keycloak.getAccessToken("alice");
    }

    /** Creates a category and returns its identifier. */
    private String createCategory(String label) {
        return given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"label\": \"%s\" }".formatted(label))
                .when().post("/api/categories")
                .then().statusCode(200)
                .extract().path("id");
    }

    /** Adds a title to the collection and returns its identifier. */
    private String addTitle(String title) {
        return given().auth().oauth2(token()).contentType("application/json")
                .body("""
                        { "book": { "kind": "BOOK", "title": "%s", "authors": "Category Test" },
                          "status": "OWNED" }
                        """.formatted(title))
                .when().post("/api/library")
                .then().statusCode(201)
                .extract().path("id");
    }

    private void rank(String itemId, String categoryId) {
        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"categoryId\": \"%s\" }".formatted(categoryId))
                .when().put("/api/library/" + itemId + "/rank")
                .then().statusCode(200);
    }

    private String builtinId(String code) {
        return given().auth().oauth2(token())
                .when().get("/api/categories")
                .then().statusCode(200)
                .extract().path("find { it.code == '%s' }.id".formatted(code));
    }

    @Test
    void requiresAuthentication() {
        String id = UUID.randomUUID().toString();
        given().contentType("application/json").body("{ \"label\": \"Anonyme\" }")
                .when().put("/api/categories/" + id)
                .then().statusCode(401);
        given().when().delete("/api/categories/" + id)
                .then().statusCode(401);
    }

    /**
     * The journey the issue asks for: a category created by hand becomes a shelf like any
     * other — it is listed, a title can be filed under it, and the collection can be
     * filtered on it.
     */
    @Test
    void aCreatedCategoryCanBeListedAssignedAndFilteredOn() {
        String id = createCategory("Doré");

        given().auth().oauth2(token())
                .when().get("/api/categories")
                .then().statusCode(200)
                .body("find { it.id == '%s' }.code".formatted(id), is("dore"))
                .body("find { it.id == '%s' }.builtin".formatted(id), is(false));

        String item = addTitle("Category - assigned");
        rank(item, id);

        given().auth().oauth2(token())
                .when().get("/api/library/" + item)
                .then().statusCode(200)
                .body("rankCode", is("dore"));

        given().auth().oauth2(token()).queryParam("rank", "dore")
                .when().get("/api/library")
                .then().statusCode(200)
                .body("items.id", hasItem(item));
    }

    /**
     * Renaming changes the code with the label — the two are one thing, the second derived
     * from the first — and the titles keep their rank: they point at the category by
     * identifier, not by code.
     */
    @Test
    void renamingKeepsTheTitlesFiledUnderTheCategory() {
        String id = createCategory("Temporaire");
        String item = addTitle("Category - renamed");
        rank(item, id);

        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"label\": \"Relectures\" }")
                .when().put("/api/categories/" + id)
                .then().statusCode(200)
                .body("label", is("Relectures"))
                .body("code", is("relectures"));

        given().auth().oauth2(token())
                .when().get("/api/library/" + item)
                .then().statusCode(200)
                .body("rankCode", is("relectures"));
    }

    /** Renaming a category to the name it already carries is not a conflict with itself. */
    @Test
    void renamingToTheSameLabelIsAccepted() {
        String id = createCategory("Inchangée");

        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"label\": \"Inchangée\" }")
                .when().put("/api/categories/" + id)
                .then().statusCode(200)
                .body("code", is("inchangee"));
    }

    /**
     * The decision this feature turns on: deleting a category unfiles the titles that were
     * in it and keeps every one of them. A rank is a label stuck on a book, and removing the
     * label cannot remove the book.
     */
    @Test
    void deletingACategoryUnranksItsTitlesWithoutDeletingThem() {
        String id = createCategory("À revendre");
        String item = addTitle("Category - unranked");
        rank(item, id);

        given().auth().oauth2(token())
                .when().delete("/api/categories/" + id)
                .then().statusCode(204);

        given().auth().oauth2(token())
                .when().get("/api/categories")
                .then().statusCode(200)
                .body("id", not(hasItem(id)));

        // The title is still in the collection, simply without a rank.
        given().auth().oauth2(token())
                .when().get("/api/library/" + item)
                .then().statusCode(200)
                .body("rankCode", is(nullValue()));
    }

    /** A category nobody filed anything under goes away just the same. */
    @Test
    void anUnusedCategoryCanBeDeleted() {
        String id = createCategory("Jamais utilisée");

        given().auth().oauth2(token())
                .when().delete("/api/categories/" + id)
                .then().statusCode(204);
    }

    /**
     * The built-ins are shared rows: renaming one would rename it for every account. The
     * refusal is a 403 and not a 404, since the caller can see them in the listing.
     */
    @Test
    void builtinsCanBeNeitherRenamedNorDeleted() {
        String or = builtinId("or");

        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"label\": \"Platine\" }")
                .when().put("/api/categories/" + or)
                .then().statusCode(403);

        given().auth().oauth2(token())
                .when().delete("/api/categories/" + or)
                .then().statusCode(403);

        given().auth().oauth2(token())
                .when().get("/api/categories")
                .then().statusCode(200)
                .body("find { it.code == 'or' }.label", is("Or"));
    }

    /**
     * One name per user. The check covers the built-ins, whose codes live in the same space:
     * a second category coded {@code or} would answer to the same {@code ?rank=or} filter.
     */
    @Test
    void aNameAlreadyUsedIsRefused() {
        createCategory("Coup de cœur");

        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"label\": \"COUP DE COEUR\" }")
                .when().post("/api/categories")
                .then().statusCode(409);

        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"label\": \"Or\" }")
                .when().post("/api/categories")
                .then().statusCode(409);
    }

    /** Same rule on a rename: it must not land on a name the user already has. */
    @Test
    void renamingOntoAnExistingNameIsRefused() {
        createCategory("Prêtés");
        String other = createCategory("Perdus");

        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"label\": \"Prêtés\" }")
                .when().put("/api/categories/" + other)
                .then().statusCode(409);
    }

    @Test
    void anEmptyLabelIsRejected() {
        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"label\": \"   \" }")
                .when().post("/api/categories")
                .then().statusCode(400);
    }

    @Test
    void anUnknownCategoryIsNotFound() {
        String unknown = UUID.randomUUID().toString();

        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"label\": \"Fantôme\" }")
                .when().put("/api/categories/" + unknown)
                .then().statusCode(404);

        given().auth().oauth2(token())
                .when().delete("/api/categories/" + unknown)
                .then().statusCode(404);
    }
}
