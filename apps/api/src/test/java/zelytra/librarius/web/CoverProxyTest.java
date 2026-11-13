package zelytra.librarius.web;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import zelytra.librarius.covers.BnfCoverClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

/** The cover proxy behind the real cache table, with only the BnF HTTP call replaced. */
@QuarkusTest
class CoverProxyTest {

    @InjectMock
    @RestClient
    BnfCoverClient upstream;

    @BeforeEach
    void reset() {
        Mockito.reset(upstream);
    }

    private static byte[] jpeg(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private void upstreamAnswers(RestResponse<byte[]> response) {
        Mockito.when(upstream.cover(anyString(), anyString(), anyInt()))
                .thenReturn(Uni.createFrom().item(response));
    }

    private void upstreamCalls(int times) {
        Mockito.verify(upstream, Mockito.times(times)).cover(anyString(), anyString(), anyInt());
    }

    @Test
    void servesAResizedThumbnailAnonymouslyAndCachesIt() throws Exception {
        upstreamAnswers(RestResponse.ResponseBuilder.ok(jpeg(600, 900)).type("image/jpeg").build());

        byte[] body = given().when().get("/api/covers/bnf/cb458523524")
                .then().statusCode(200)
                .contentType("image/jpeg")
                .header("Cache-Control", "public, max-age=2592000, immutable")
                .extract().asByteArray();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(body));
        assertNotNull(image);
        assertEquals(400, image.getHeight());
        assertEquals(267, image.getWidth());

        given().when().get("/api/covers/bnf/cb458523524").then().statusCode(200);
        upstreamCalls(1);
    }

    @Test
    void neverUpscales() throws Exception {
        upstreamAnswers(RestResponse.ResponseBuilder.ok(jpeg(100, 200)).type("image/jpeg").build());

        byte[] body = given().when().get("/api/covers/bnf/cb36194659h")
                .then().statusCode(200).extract().asByteArray();
        assertEquals(200, ImageIO.read(new ByteArrayInputStream(body)).getHeight());
    }

    @Test
    void aRecordWithoutCoverIsAMissRememberedWithoutRefetching() {
        upstreamAnswers(RestResponse.ResponseBuilder.<byte[]>create(500)
                .entity("<html>error</html>".getBytes()).type("text/html").build());

        given().when().get("/api/covers/bnf/cb43664443h")
                .then().statusCode(404).header("Cache-Control", "public, max-age=86400");
        given().when().get("/api/covers/bnf/cb43664443h").then().statusCode(404);
        upstreamCalls(1);
    }

    /**
     * An outage is not "no cover": remembering it would hide the cover for a week after a
     * failure of a few minutes. The next display asks again.
     */
    @Test
    void anUnreachableUpstreamIsAMissThatIsNotRemembered() {
        Mockito.when(upstream.cover(anyString(), anyString(), anyInt()))
                .thenReturn(Uni.createFrom().failure(new IllegalStateException("timed out")));

        given().when().get("/api/covers/bnf/cb300000001a").then().statusCode(404);
        given().when().get("/api/covers/bnf/cb300000001a").then().statusCode(404);
        upstreamCalls(2);
    }

    @Test
    void aGatewayErrorIsNotRememberedEither() {
        upstreamAnswers(RestResponse.ResponseBuilder.<byte[]>create(503)
                .entity("unavailable".getBytes()).type("text/plain").build());

        given().when().get("/api/covers/bnf/cb300000009k").then().statusCode(404);
        given().when().get("/api/covers/bnf/cb300000009k").then().statusCode(404);
        upstreamCalls(2);
    }

    @Test
    void anUndecodableImageIsAMiss() {
        upstreamAnswers(RestResponse.ResponseBuilder.ok("not an image".getBytes()).type("image/jpeg").build());

        given().when().get("/api/covers/bnf/cb300000002b").then().statusCode(404);
    }

    @Test
    void aMalformedArkIsRefusedWithoutCallingUpstream() {
        given().when().get("/api/covers/bnf/cbXYZ").then().statusCode(404);
        given().when().get("/api/covers/bnf/cb458523524/../../etc").then().statusCode(404);
        given().when().get("/api/covers/bnf/..%2F..%2Fetc").then().statusCode(404);
        Mockito.verifyNoInteractions(upstream);
    }

    @Test
    void everyOtherEndpointStaysAuthenticated() {
        given().when().get("/api/library").then().statusCode(401);
    }
}
