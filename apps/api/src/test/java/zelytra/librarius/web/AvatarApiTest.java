package zelytra.librarius.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static zelytra.librarius.web.SocialTestSupport.id;
import static zelytra.librarius.web.SocialTestSupport.relation;
import static zelytra.librarius.web.SocialTestSupport.strangers;
import static zelytra.librarius.web.SocialTestSupport.token;

/** #500: a member's profile picture — what is accepted, what is stored, who sees it. */
@QuarkusTest
class AvatarApiTest {

    static byte[] image(String format, int width, int height) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    /** A JPEG carrying an EXIF segment with a location in it, right after the SOI marker. */
    static byte[] jpegWithExif() throws Exception {
        byte[] jpeg = image("jpeg", 400, 300);
        byte[] payload = "Exif\0\0GPSLatitude 48.8566 GPSLongitude 2.3522".getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(0xFF);
        out.write(0xE1);
        int length = payload.length + 2;
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.write(payload);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static void upload(String user, byte[] body, String type, int expected) {
        given().auth().oauth2(token(user)).contentType(type).body(body)
                .when().put("/api/me/avatar").then().statusCode(expected);
    }

    private static byte[] avatarOf(String viewer, String member, int expected) {
        return given().auth().oauth2(token(viewer)).when().get("/api/users/" + id(member) + "/avatar")
                .then().statusCode(expected).extract().asByteArray();
    }

    @BeforeEach
    void clean() {
        strangers();
        given().auth().oauth2(token("bob")).when().delete("/api/me/avatar").then().statusCode(204);
    }

    @Test
    void anUploadIsReEncodedToASquareWithoutItsMetadata() throws Exception {
        upload("bob", jpegWithExif(), "image/jpeg", 204);

        byte[] stored = avatarOf("alice", "bob", 200);
        BufferedImage read = ImageIO.read(new ByteArrayInputStream(stored));
        assertEquals(256, read.getWidth());
        assertEquals(256, read.getHeight());
        String raw = new String(stored, StandardCharsets.ISO_8859_1);
        assertFalse(raw.contains("Exif"), "the EXIF segment is gone");
        assertFalse(raw.contains("GPSLatitude"));
    }

    @Test
    void theAvatarIsServedSafely() throws Exception {
        upload("bob", image("png", 300, 300), "image/png", 204);

        given().auth().oauth2(token("alice")).when().get("/api/users/" + id("bob") + "/avatar")
                .then().statusCode(200)
                .header("Content-Type", is("image/jpeg"))
                .header("X-Content-Type-Options", is("nosniff"))
                .header("Content-Security-Policy", is("default-src 'none'"));
        assertNotNull(given().auth().oauth2(token("bob")).when().get("/api/me").then().statusCode(200)
                .extract().path("avatarVersion"));
    }

    @Test
    void onlyARealJpegOrPngIsAccepted() throws Exception {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        upload("bob", svg, "image/png", 415);
        byte[] html = "<html><body>hello</body></html>".getBytes(StandardCharsets.UTF_8);
        upload("bob", html, "image/jpeg", 415);
        // A real magic number with nothing decodable behind it.
        upload("bob", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 1, 2, 3, 4}, "image/jpeg", 415);
        upload("bob", new byte[0], "image/jpeg", 400);
    }

    @Test
    void anOversizedFileOrImageIsRefused() throws Exception {
        upload("bob", new byte[2 * 1024 * 1024 + 1], "image/jpeg", 413);
        // Wider than any photo needs: decoding it is refused before the pixels are allocated.
        upload("bob", image("png", 9000, 4), "image/png", 413);
    }

    @Test
    void aMemberRemovesTheirOwnPicture() throws Exception {
        upload("bob", image("jpeg", 64, 64), "image/jpeg", 204);
        given().auth().oauth2(token("bob")).when().delete("/api/me/avatar").then().statusCode(204);

        avatarOf("alice", "bob", 404);
        assertNull(given().auth().oauth2(token("bob")).when().get("/api/me").then().extract().path("avatarVersion"));
    }

    @Test
    void aBlockHidesItAndAnUnknownMemberHasNone() throws Exception {
        upload("bob", image("jpeg", 64, 64), "image/jpeg", 204);
        relation("bob", "alice", "block", true);

        avatarOf("alice", "bob", 404);
        given().auth().oauth2(token("alice")).when().get("/api/users/nobody/avatar").then().statusCode(404);
        given().when().get("/api/users/" + id("bob") + "/avatar").then().statusCode(401);
        given().contentType("image/jpeg").body(image("jpeg", 8, 8)).when().put("/api/me/avatar").then().statusCode(401);
    }
}
