package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

/** Vérifie que les métriques Prometheus sont exposées sur /q/metrics. */
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
}
