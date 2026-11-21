package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static zelytra.librarius.web.SocialTestSupport.token;

/**
 * #573: how many members said they live in each country, for the admins' globe. A count per
 * country and nothing else — never who — and only from the country members filled themselves.
 */
@QuarkusTest
class AdminCountriesApiTest {

    private static void country(String user, String country) {
        given().auth().oauth2(token(user)).contentType("application/json")
                .body("{ \"displayName\": \"%s\", \"locale\": \"fr\", \"country\": %s, \"publicAccount\": false }"
                        .formatted(user, country == null ? "null" : "\"" + country + "\""))
                .when().patch("/api/me").then().statusCode(200);
    }

    @AfterEach
    void clear() {
        country("bob", null);
        country("carol", null);
    }

    private static List<Map<String, Object>> countries() {
        return given().auth().oauth2(token("alice")).when().get("/api/admin/countries")
                .then().statusCode(200).extract().jsonPath().getList("$");
    }

    @Test
    void countsTheMembersPerCountryTheyFilledAndNothingMore() {
        country("bob", "NZ");
        country("carol", "NZ");

        List<Map<String, Object>> rows = countries();
        Map<String, Object> nz = rows.stream().filter(r -> "NZ".equals(r.get("country"))).findFirst().orElseThrow();

        assertEquals(2, ((Number) nz.get("members")).intValue());
        // An aggregate only: no identifier, no name, nothing that points at a member.
        rows.forEach(r -> assertEquals(java.util.Set.of("country", "members"), r.keySet()));

        country("carol", null);
        Map<String, Object> after = countries().stream().filter(r -> "NZ".equals(r.get("country"))).findFirst().orElseThrow();
        assertEquals(1, ((Number) after.get("members")).intValue());
    }

    @Test
    void aMemberWithoutACountryIsNotCounted() {
        country("bob", null);
        country("carol", null);
        assertTrue(countries().stream().noneMatch(r -> r.get("country") == null));
    }

    @Test
    void onlyAnAdminSeesIt() {
        given().auth().oauth2(token("bob")).when().get("/api/admin/countries").then().statusCode(403);
        given().when().get("/api/admin/countries").then().statusCode(401);
    }
}
