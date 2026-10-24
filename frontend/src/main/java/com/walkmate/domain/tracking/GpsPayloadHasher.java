package com.walkmate.domain.tracking;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Android half of the versioned canonical GPS payload encoding.
 *
 * <p>This MUST stay byte-for-byte identical to the backend
 * {@code com.walkmate.domain.tracking.GpsPayloadEncoder}. The server
 * recomputes the digest from the request and rejects any mismatch, so a
 * divergence here is not a cosmetic issue — every batch would be refused with
 * {@code TRACKING_HASH_MISMATCH}.</p>
 *
 * <pre>
 *   magic[4]      = 'G' 'P' 'S' '1'
 *   hdr[12]       = u32 len(session_id), u32 len(user_id), u32 len(stream_id)
 *   session_id    = UTF-8
 *   user_id       = UTF-8   (AUTHENTICATED user id, not a request field)
 *   stream_id     = UTF-8
 *   range[16]     = i64 seq_start, i64 seq_end   (big-endian)
 *   count[4]      = u32 point count
 *   per point     = i64 sequence, i64 recordedAt, f64 lat, f64 lng, f32 accuracy
 *                   (all big-endian, -0.0 normalized to +0.0)
 * </pre>
 *
 * <p>JSON is deliberately not hashed: field order and number formatting are
 * free to differ between the Android serializer and the server parser.</p>
 */
public final class GpsPayloadHasher {

    public static final byte[] MAGIC = {'G', 'P', 'S', '1'};

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private GpsPayloadHasher() {}

    /** A single canonical point — mirrors the backend {@code GpsPayloadEncoder.Point}. */
    public static final class Point {
        public final long sequence;
        public final long recordedAt;
        public final double lat;
        public final double lng;
        public final float accuracy;

        public Point(long sequence, long recordedAt, double lat, double lng, float accuracy) {
            this.sequence = sequence;
            this.recordedAt = recordedAt;
            this.lat = lat;
            this.lng = lng;
            this.accuracy = accuracy;
        }
    }

    /**
     * Computes the lower-case hex SHA-256 the client declares as
     * {@code payload_hash}.
     *
     * @param userId the authenticated user id — the same value the server binds
     *               into the encoding, so it must not come from a request field
     */
    public static String hash(String sessionId, String userId, String streamId,
                              long seqStart, long seqEnd, List<Point> points) {
        byte[] session = sessionId.getBytes(StandardCharsets.UTF_8);
        byte[] user    = userId.getBytes(StandardCharsets.UTF_8);
        byte[] stream  = streamId.getBytes(StandardCharsets.UTF_8);

        int size = MAGIC.length + 3 * Integer.BYTES
                + session.length + user.length + stream.length
                + 2 * Long.BYTES
                + Integer.BYTES
                + points.size() * (2 * Long.BYTES + 2 * Double.BYTES + Float.BYTES);

        ByteBuffer buf = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
        buf.put(MAGIC);
        buf.putInt(session.length);
        buf.putInt(user.length);
        buf.putInt(stream.length);
        buf.put(session);
        buf.put(user);
        buf.put(stream);

        buf.putLong(seqStart);
        buf.putLong(seqEnd);

        buf.putInt(points.size());
        for (int i = 0; i < points.size(); i++) {
            Point p = points.get(i);
            long expected = seqStart + i;
            if (p.sequence != expected) {
                throw new IllegalArgumentException(
                        "Point at index " + i + " has sequence " + p.sequence
                                + " but canonical encoding requires " + expected);
            }
            buf.putLong(p.sequence);
            buf.putLong(p.recordedAt);
            buf.putDouble(normalize(p.lat));
            buf.putDouble(normalize(p.lng));
            buf.putFloat(normalize(p.accuracy));
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(buf.array()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable on this device", e);
        }
    }

    /**
     * {@code HexFormat} is API 33+; this project targets API 24, so the digest is
     * formatted by hand. Output must match the server's {@code HexFormat.of()}
     * (lower-case, no separators).
     */
    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xFF;
            out[i * 2]     = HEX[b >>> 4];
            out[i * 2 + 1] = HEX[b & 0x0F];
        }
        return new String(out);
    }

    /** {@code +0.0} and {@code -0.0} compare equal but hash differently. */
    private static double normalize(double v) {
        return v == 0.0 ? 0.0 : v;
    }

    private static float normalize(float v) {
        return v == 0.0f ? 0.0f : v;
    }
}
