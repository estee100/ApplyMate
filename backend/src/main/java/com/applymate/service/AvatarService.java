package com.applymate.service;

import com.applymate.error.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.Optional;

/**
 * Profile pictures. An upload is treated as hostile:
 * <ol>
 *   <li>its pixel dimensions are read from the header before anything is decoded, so a
 *       tiny file claiming 50,000 x 50,000 pixels (a decompression bomb) is refused
 *       without allocating gigabytes;</li>
 *   <li>it is fully decoded, so anything that is not really an image is rejected
 *       whatever its name or declared type;</li>
 *   <li>it is cropped to a centred square, scaled to 256px and re-encoded as PNG, which
 *       drops EXIF metadata (camera, timestamps, GPS location) and any payload hidden in
 *       the original bytes.</li>
 * </ol>
 */
@Service
public class AvatarService {

    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int MAX_DIMENSION = 6000;
    static final int SIZE = 256;

    private final JdbcTemplate jdbc;
    private final CurrentUser currentUser;
    private final Clock clock;

    public AvatarService(JdbcTemplate jdbc, CurrentUser currentUser, Clock clock) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    public record Avatar(byte[] png, Instant updatedAt) {}

    public Instant upload(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw ApiException.badRequest("That file is empty.");
        if (bytes.length > MAX_BYTES) throw ApiException.badRequest("Profile pictures must be 2 MB or smaller.");
        byte[] png = normalize(bytes);
        Instant now = Instant.now(clock);
        jdbc.update("""
                INSERT INTO user_avatars (user_id, image, updated_at) VALUES (?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET image = EXCLUDED.image, updated_at = EXCLUDED.updated_at""",
                currentUser.id(), png, Timestamp.from(now));
        return now;
    }

    public Optional<Avatar> get() {
        return jdbc.query("SELECT image, updated_at FROM user_avatars WHERE user_id = ?",
                rs -> rs.next() ? Optional.of(new Avatar(rs.getBytes(1), rs.getTimestamp(2).toInstant())) : Optional.empty(),
                currentUser.id());
    }

    public Optional<Instant> updatedAt(Long userId) {
        return jdbc.query("SELECT updated_at FROM user_avatars WHERE user_id = ?",
                rs -> rs.next() ? Optional.of(rs.getTimestamp(1).toInstant()) : Optional.empty(), userId);
    }

    public void delete() {
        jdbc.update("DELETE FROM user_avatars WHERE user_id = ?", currentUser.id());
    }

    static byte[] normalize(byte[] bytes) {
        BufferedImage source = decodeSafely(bytes);
        int side = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - side) / 2;
        int y = (source.getHeight() - side) / 2;

        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, SIZE, SIZE, x, y, x + side, y + side, null);
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            ImageIO.write(out, "png", buffer);
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BufferedImage decodeSafely(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) throw notAnImage();
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                // Header only: no pixels are decoded until the size is known to be sane.
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    throw ApiException.badRequest("That image is too large. Use one under " + MAX_DIMENSION + " pixels a side.");
                }
                BufferedImage image = reader.read(0);
                if (image == null) throw notAnImage();
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) throw api;
            throw notAnImage();
        }
    }

    private static ApiException notAnImage() {
        return ApiException.badRequest("Upload a PNG, JPEG, GIF or BMP image.");
    }
}
