package com.walkmate.infrastructure.util;

import java.util.List;

/**
 * Implements the <a href="https://developers.google.com/maps/documentation/utilities/polylinealgorithm">
 * Google Encoded Polyline Algorithm</a>.
 *
 * <p>Encodes a sequence of (lat, lng) pairs into a compact ASCII string.
 * Each coordinate delta is encoded in 5-bit chunks, shifted by +63, and
 * sign-extended via left-shift-then-invert for negative values.</p>
 */
public final class PolylineEncoder {

    private PolylineEncoder() {}

    /**
     * Encodes a list of lat/lng pairs to a Google Encoded Polyline string.
     *
     * @param lats latitudes in decimal degrees
     * @param lngs longitudes in decimal degrees (same length as lats)
     * @return encoded polyline string, or empty string if lats is empty
     */
    public static String encode(List<Double> lats, List<Double> lngs) {
        if (lats == null || lats.isEmpty()) return "";

        StringBuilder sb   = new StringBuilder();
        int           prevLat = 0;
        int           prevLng = 0;

        for (int i = 0; i < lats.size(); i++) {
            int lat = (int) Math.round(lats.get(i) * 1e5);
            int lng = (int) Math.round(lngs.get(i) * 1e5);
            encodeChunk(lat - prevLat, sb);
            encodeChunk(lng - prevLng, sb);
            prevLat = lat;
            prevLng = lng;
        }
        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void encodeChunk(int value, StringBuilder out) {
        // Step 1: left-shift by 1; if negative, invert all bits
        value = value < 0 ? ~(value << 1) : (value << 1);

        // Step 2: split into 5-bit groups, OR with 0x20 when more groups follow
        while (value >= 0x20) {
            out.append((char) ((0x20 | (value & 0x1f)) + 63));
            value >>= 5;
        }
        out.append((char) (value + 63));
    }
}
