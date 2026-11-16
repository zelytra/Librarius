package zelytra.librarius.account;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.domain.repository.UserBlockRepository;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Profile pictures (#500), handled as untrusted input from end to end.
 *
 * <ul>
 *   <li>The size is capped before anything is decoded, and only JPEG and PNG are accepted —
 *       recognised by their <em>magic bytes</em>, never by the declared type or a file name, so
 *       an SVG, an HTML page or a script renamed {@code .png} is refused (415).</li>
 *   <li>The dimensions are read from the header and checked before the pixels are allocated: a
 *       small file announcing a huge image (a decompression bomb) is refused (413).</li>
 *   <li>What is stored is never the upload: the image is decoded, cropped to its centre square
 *       and re-encoded as a 256-pixel JPEG. EXIF — a phone photo's GPS position among it — and
 *       anything hidden after the image data are gone, and the file served is one this
 *       service wrote.</li>
 * </ul>
 *
 * <p>Reading follows the display name's rule (#201): any signed-in member, never across a block.
 */
@ApplicationScoped
public class AvatarService {

    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int MAX_SIDE = 8000;
    static final long MAX_PIXELS = 40_000_000L;
    static final int SIZE = 256;
    private static final float QUALITY = 0.85f;

    @Inject
    EntityManager em;

    @Inject
    UserBlockRepository blocks;

    @Transactional
    public void store(String userId, byte[] upload) {
        if (upload == null || upload.length == 0) {
            throw new WebApplicationException("Aucune image reçue.", Status.BAD_REQUEST);
        }
        if (upload.length > MAX_BYTES) {
            throw new WebApplicationException("Image trop lourde (2 Mo au plus).", Status.REQUEST_ENTITY_TOO_LARGE);
        }
        if (!isJpeg(upload) && !isPng(upload)) {
            throw unsupported();
        }
        byte[] content = reencode(decode(upload));
        AppUser user = em.find(AppUser.class, userId);
        em.createNativeQuery("""
                        insert into user_avatar (user_id, content, updated_at) values (:user, :content, now())
                        on conflict (user_id) do update set content = excluded.content, updated_at = now()""")
                .setParameter("user", userId)
                .setParameter("content", content)
                .executeUpdate();
        user.avatarVersion = System.currentTimeMillis();
    }

    @Transactional
    public void remove(String userId) {
        em.createNativeQuery("delete from user_avatar where user_id = :user").setParameter("user", userId)
                .executeUpdate();
        AppUser user = em.find(AppUser.class, userId);
        if (user != null) {
            user.avatarVersion = null;
        }
    }

    /** A member's picture as the viewer may see it: none for an unknown member or across a block. */
    @Transactional
    public Optional<byte[]> read(String viewerId, String memberId) {
        if (!viewerId.equals(memberId) && blocks.isBlockBetween(viewerId, memberId)) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        List<byte[]> rows = em.createNativeQuery("select content from user_avatar where user_id = :user")
                .setParameter("user", memberId)
                .getResultList();
        return rows.stream().findFirst();
    }

    private static boolean isJpeg(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] b) {
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        if (b.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (b[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    /** Decodes the image, its dimensions checked from the header before the pixels exist. */
    private static BufferedImage decode(byte[] upload) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw unsupported();
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase();
                if (!format.equals("jpeg") && !format.equals("png")) {
                    throw unsupported();
                }
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1) {
                    throw unsupported();
                }
                if (width > MAX_SIDE || height > MAX_SIDE || (long) width * height > MAX_PIXELS) {
                    throw new WebApplicationException("Image trop grande.", Status.REQUEST_ENTITY_TOO_LARGE);
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw unsupported();
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof WebApplicationException web) {
                throw web;
            }
            throw unsupported();
        }
    }

    /** The centre square, scaled to {@link #SIZE}, as a JPEG with no metadata. */
    private static byte[] reencode(BufferedImage image) {
        int side = Math.min(image.getWidth(), image.getHeight());
        int x = (image.getWidth() - side) / 2;
        int y = (image.getHeight() - side) / 2;
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        var g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, SIZE, SIZE, x, y, x + side, y + side, Color.WHITE, null);
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
        } catch (IOException e) {
            throw new IllegalStateException("JPEG encoding failed", e);
        } finally {
            writer.dispose();
        }
    }

    private static WebApplicationException unsupported() {
        return new WebApplicationException("Seules les images JPEG et PNG sont acceptées.",
                Status.UNSUPPORTED_MEDIA_TYPE);
    }
}
