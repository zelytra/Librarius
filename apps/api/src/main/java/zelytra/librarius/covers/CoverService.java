package zelytra.librarius.covers;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse;
import zelytra.librarius.domain.CoverCache;
import zelytra.librarius.domain.repository.CoverCacheRepository;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Serves resized BnF cover thumbnails, cached in PostgreSQL.
 *
 * <p>The ark id is validated against a strict pattern and is the only thing that reaches the
 * upstream URL, which sits on a fixed host: this is not an open proxy.
 *
 * <p>Any failure — upstream error, timeout, non-image, oversize, undecodable — is a miss and
 * is cached as one, so a record without a cover costs one fetch a week, not one per display.
 */
@ApplicationScoped
public class CoverService {

    private static final Logger LOG = Logger.getLogger(CoverService.class);

    static final Pattern ARK = Pattern.compile("^cb[0-9]{8,9}[0-9a-z]$");
    static final int HEIGHT = 400;
    private static final float QUALITY = 0.8f;
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration MISS_RETRY = Duration.ofDays(7);

    @Inject
    @RestClient
    BnfCoverClient client;

    @Inject
    CoverCacheRepository cache;

    /** True when {@code ark} is a well-formed BnF ark id. */
    public static boolean isValid(String ark) {
        return ark != null && ARK.matcher(ark).matches();
    }

    /**
     * The JPEG thumbnail of the record's cover, or empty when there is none. The ark must have
     * been validated with {@link #isValid}.
     */
    public Optional<byte[]> bnfCover(String ark) {
        String key = "bnf:" + ark;
        // Read, fetch and write are three separate steps: the upstream call, which can take
        // seconds, must not hold a database transaction (and its connection) open.
        CoverCache cached = QuarkusTransaction.requiringNew().call(() -> cache.findById(key));
        if (cached != null && (cached.content != null
                || cached.fetchedAt.isAfter(OffsetDateTime.now().minus(MISS_RETRY)))) {
            return Optional.ofNullable(cached.content);
        }
        byte[] thumbnail = fetch(ark);
        store(key, thumbnail);
        return Optional.ofNullable(thumbnail);
    }

    private byte[] fetch(String ark) {
        try {
            RestResponse<byte[]> response = client.cover("NE", "ark:/12148/" + ark, 1)
                    .await().atMost(FETCH_TIMEOUT);
            MediaType type = response.getMediaType();
            byte[] body = response.getEntity();
            if (response.getStatus() / 100 != 2 || type == null || !"image".equals(type.getType())
                    || body == null || body.length == 0 || body.length > MAX_BYTES) {
                return null;
            }
            return resize(body);
        } catch (Exception e) {
            LOG.debugf("No BnF cover for %s: %s", ark, e.toString());
            return null;
        }
    }

    /** Scales to {@link #HEIGHT} keeping the ratio, never upscaling; null if undecodable. */
    static byte[] resize(byte[] source) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(source));
        if (image == null || image.getWidth() < 1 || image.getHeight() < 1) {
            return null;
        }
        int height = Math.min(HEIGHT, image.getHeight());
        int width = Math.max(1, Math.round((float) image.getWidth() * height / image.getHeight()));
        // JPEG has no alpha channel: draw onto an opaque white RGB canvas.
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, width, height, Color.WHITE, null);
        } finally {
            g.dispose();
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(bytes)) {
                writer.setOutput(stream);
                writer.write(null, new IIOImage(out, null, null), param);
            }
            return bytes.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private void store(String key, byte[] content) {
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                CoverCache row = cache.findById(key);
                if (row == null) {
                    row = new CoverCache();
                    row.cacheKey = key;
                    row.content = content;
                    row.fetchedAt = OffsetDateTime.now();
                    cache.persist(row);
                } else {
                    row.content = content;
                    row.fetchedAt = OffsetDateTime.now();
                }
            });
        } catch (RuntimeException e) {
            // A concurrent request for the same ark inserted the row first (primary key
            // conflict): its copy is equivalent, so there is nothing left to do.
            if (!(e instanceof PersistenceException) && !(e.getCause() instanceof PersistenceException)) {
                throw e;
            }
            LOG.debugf("Cover %s was cached concurrently", key);
        }
    }
}
