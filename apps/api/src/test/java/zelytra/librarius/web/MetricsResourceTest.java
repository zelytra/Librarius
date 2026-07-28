package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsString;

/**
 * Checks the unauthenticated management endpoints: the Prometheus metrics on
 * /q/metrics and the health checks backing the Kubernetes probes on /q/health.
 */
@QuarkusTest
class MetricsResourceTest {

    @Test
    void metricsEndpointExposesPrometheusMetrics() {
        given()
                .when().get("/q/metrics")
                .then()
                .statusCode(200)
                .body(containsString("jvm_memory_used_bytes"));
    }

    @Test
    void healthEndpointIsUp() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"));
    }

    @Test
    void probeEndpointsAreUp() {
        given()
                .when().get("/q/health/ready")
                .then()
                .statusCode(200)
                .body("status", is("UP"));

        given()
                .when().get("/q/health/live")
                .then()
                .statusCode(200)
                .body("status", is("UP"));
    }
}
