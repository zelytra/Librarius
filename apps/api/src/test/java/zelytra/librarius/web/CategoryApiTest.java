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
                          "status": "TO_READ" }
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
                // One of the reader's own categories: among the title's categories (#646).
                .body("categoryCodes", org.hamcrest.Matchers.contains("dore"));

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
                .body("categoryCodes", org.hamcrest.Matchers.contains("relectures"));
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

    /** Platine sits above Or (#608): a shared, read-only built-in listed before the others. */
    @Test
    void platineIsTheFirstBuiltinAndCannotBeTakenOrChanged() {
        given().auth().oauth2(token())
                .when().get("/api/categories")
                .then().statusCode(200)
                .body("[0].code", is("platine"))
                .body("[0].label", is("Platine"))
                .body("[0].builtin", is(true))
                .body("[1].code", is("or"));

        String platine = builtinId("platine");
        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"label\": \"Diamant\" }")
                .when().put("/api/categories/" + platine)
                .then().statusCode(403);
        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"label\": \"Platine\" }")
                .when().post("/api/categories")
                .then().statusCode(409);
    }

    @Test
    void aTitleCanBeFiledUnderPlatineAndFilteredOn() {
        String item = addTitle("Platine Test Title");
        rank(item, builtinId("platine"));

        given().auth().oauth2(token())
                .when().get("/api/library?rank=platine&size=200")
                .then().statusCode(200)
                .body("items.id", hasItem(item));
    }

    /**
     * The built-ins are shared rows: renaming one would rename it for every account. The
     * refusal is a 403 and not a 404, since the caller can see them in the listing.
     */
    @Test
    void builtinsCanBeNeitherRenamedNorDeleted() {
        String or = builtinId("or");

        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"label\": \"Vermeil\" }")
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

    /** #599: a category of the reader's own carries the colour they picked, and can change it. */
    @Test
    void aCategoryKeepsTheColourPickedAndChangesIt() {
        String id = given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"label\": \"Teinte %s\", \"color\": \"#3b7a57\" }".formatted(java.util.UUID.randomUUID()))
                .when().post("/api/categories")
                .then().statusCode(200).body("color", org.hamcrest.Matchers.is("#3b7a57"))
                .extract().path("id");

        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"label\": \"Teinte changée %s\", \"color\": \"#8a4fff\" }".formatted(java.util.UUID.randomUUID()))
                .when().put("/api/categories/" + id)
                .then().statusCode(200).body("color", org.hamcrest.Matchers.is("#8a4fff"));

        // A rename that says nothing of the colour keeps it.
        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"label\": \"Teinte gardée %s\" }".formatted(java.util.UUID.randomUUID()))
                .when().put("/api/categories/" + id)
                .then().statusCode(200).body("color", org.hamcrest.Matchers.is("#8a4fff"));
    }

    /** Only a colour of the form #rrggbb is stored: it is drawn straight into the page. */
    @Test
    void refusesAColourThatIsNotAHexCode() {
        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"label\": \"Mauvaise teinte\", \"color\": \"red;background:url(x)\" }")
                .when().post("/api/categories").then().statusCode(400);
        String id = createCategory("Teinte refusée " + java.util.UUID.randomUUID());
        given().auth().oauth2(token()).contentType("application/json")
                .body("{ \"label\": \"Teinte refusée bis\", \"color\": \"blue\" }")
                .when().put("/api/categories/" + id).then().statusCode(400);
    }

    @Test
    void anotherReadersCategoryKeepsItsColour() {
        String id = createCategory("Teinte privée " + java.util.UUID.randomUUID());
        given().auth().oauth2(keycloak.getAccessToken("bob")).contentType("application/json")
                .body("{ \"label\": \"Volée\", \"color\": \"#000000\" }")
                .when().put("/api/categories/" + id).then().statusCode(404);
    }
}
