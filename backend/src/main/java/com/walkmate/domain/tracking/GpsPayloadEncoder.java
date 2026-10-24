package com.walkmate.domain.tracking;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Versioned canonical byte encoding for GPS batch payloads.
 *
 * <h3>Why a canonical encoding and not a JSON hash?</h3>
 * JSON field order, number formatting (e.g. {@code 5.2} vs {@code 5.20} vs
 * {@code 5.2E0}) and charset handling are free for the HTTP layer to make
 * differ across clients / server round-trips, so hashing raw JSON is not a
 * stable identity. Instead both sides compute the hash from <em>this</em>
 * exact byte layout, derived from the same logical values:
 *
 * <pre>
 *   magic[4]          = 'G' 'P' 'S' '1'          // encoding version
 *   idHeader[12]      = big-endian u32 lengths:
 *                         len(session_id) len(user_id) len(stream_id)
 *   session_id        = UTF-8 bytes
 *   user_id           = UTF-8 bytes (AUTHENTICATED user, not the request value)
 *   stream_id         = UTF-8 bytes
 *   range[16]         = big-endian i64 seq_start, big-endian i64 seq_end
 *   pointCount[4]     = big-endian u32
 *   per point         = sequence[8] big-endian i64
 *                         recordedAt[8] big-endian i64 (epoch millis)
 *                         lat[8] double big-endian (after -0.0 normalization)
 *                         lng[8] double big-endian
 *                         accuracy[4] float big-endian (after -0.0 normalization)
 * </pre>
 *
 * <p>Normalization rules (both sides must apply the identical rules before
 * encoding): lat/lng/accuracy are rejected if non-finite or out of bounds
 * (handled at validation); a negative zero is replaced by +0.0 so the two
 * encodings can never diverge on that bit pattern. NaN/Double branches are
 * asserted by validation — the encoder itself documents them as a trap.</p>
 */
public final class GpsPayloadEncoder {

    /** Encoding version tag. Bump when ANY byte-level rule changes. */
    public static final byte[] MAGIC = {'G', 'P', 'S', '1'};

    public static final String HASH_ALGORITHM = "SHA-256";

    private GpsPayloadEncoder() {}

    /**
     * Computes the canonical SHA-256 hash of a batch payload.
     *
     * @param sessionId walk session id (UTF-8)
     * @param userId    authenticated user id — the header is bound to the
     *                  server-authenticated user, not the client's claim
     * @param streamId  client-minted stream id (UTF-8)
     * @param seqStart  inclusive first sequence of the batch
     * @param seqEnd    inclusive last sequence of the batch (must equal start+size-1)
     * @param points    points in strict sequence order; each point's sequence
     *                  must equal seqStart + index
     * @return lower-case hex SHA-256 digest
     */
    public static String hash(String sessionId, String userId, String streamId,
                              long seqStart, long seqEnd,
                              List<Point> points) {
        ByteBuffer buf = ByteBuffer.allocate(headSize(sessionId, userId, streamId)
                + Integer.BYTES
                + points.size() * pointSize())
                .order(ByteOrder.BIG_ENDIAN);

        // Magic
        buf.put(MAGIC);

        // idHeader: u32 lengths
        byte[] s = sessionId.getBytes(StandardCharsets.UTF_8);
        byte[] u = userId.getBytes(StandardCharsets.UTF_8);
        byte[] t = streamId.getBytes(StandardCharsets.UTF_8);
        buf.putInt(s.length);
        buf.putInt(u.length);
        buf.putInt(t.length);
        buf.put(s);
        buf.put(u);
        buf.put(t);

        // Range
        buf.putLong(seqStart);
        buf.putLong(seqEnd);

        // Points
        buf.putInt(points.size());
        for (int i = 0; i < points.size(); i++) {
            Point p = points.get(i);
            long expectedSeq = seqStart + i;
            if (p.sequence() != expectedSeq) {
                throw new IllegalArgumentException(
                        "Point at index " + i + " has sequence " + p.sequence()
                                + " but canonical encoding requires " + expectedSeq);
            }
            buf.putLong(p.sequence());
            buf.putLong(p.recordedAt());
            buf.putDouble(normalize(p.lat()));
            buf.putDouble(normalize(p.lng()));
            buf.putFloat(normalizeAccuracy(p.accuracy()));
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(buf.array()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * A single canonical point. Mirrors the Android
     * {@code PushRoutePointsRequest.RoutePointPayload} minus the legacy
     * {@code localId} (the v2 identity is {@code (stream, sequence)}).
     */
    public record Point(long sequence, long recordedAt, double lat, double lng, float accuracy) {}

    private static int headSize(String s, String u, String t) {
        return MAGIC.length
                + 3 * Integer.BYTES
                + s.getBytes(StandardCharsets.UTF_8).length
                + u.getBytes(StandardCharsets.UTF_8).length
                + t.getBytes(StandardCharsets.UTF_8).length
                + 2 * Long.BYTES;
    }

    private static int pointSize() {
        return 2 * Long.BYTES + 2 * Double.BYTES + Float.BYTES;
    }

    /**
     * The canonical encoding must be unique per logical value: floating-point
     * zero has two bit patterns ({@code +0.0} and {@code -0.0}) that compare
     * equal but would hash differently, so we normalize -0.0 to +0.0.
     */
    private static double normalize(double v) {
        return v == 0.0 ? 0.0 : v;
    }

    private static float normalizeAccuracy(float v) {
        return v == 0f ? 0f : v;
    }
}
