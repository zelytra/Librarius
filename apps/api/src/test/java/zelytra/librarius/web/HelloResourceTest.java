package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class HelloResourceTest {

    @Test
    void helloEndpointReturnsAppName() {
        given()
                .when().get("/api/hello")
                .then()
                .statusCode(200)
                .body("app", is("Librarius API"));
    }

    @Test
    void healthEndpointIsUp() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"));
    }
}
