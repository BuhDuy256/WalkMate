package com.walkmate.infrastructure.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Decodes Google Encoded Polyline strings and provides route distance calculation.
 *
 * @see <a href="https://developers.google.com/maps/documentation/utilities/polylinealgorithm">
 *      Google Encoded Polyline Algorithm</a>
 */
public final class PolylineDecoder {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private PolylineDecoder() {}

    /**
     * Decodes an encoded polyline string into a list of [lat, lng] pairs.
     *
     * @param polyline Google Encoded Polyline string
     * @return list of coordinate pairs (each element: {@code [lat, lng]}), never null
     */
    public static List<double[]> decode(String polyline) {
        List<double[]> result = new ArrayList<>();
        if (polyline == null || polyline.isEmpty()) return result;

        int index = 0;
        int len   = polyline.length();
        int lat   = 0;
        int lng   = 0;

        while (index < len) {
            // Decode latitude delta
            int b, shift = 0, deltaLat = 0;
            do {
                b      = polyline.charAt(index++) - 63;
                deltaLat |= (b & 0x1f) << shift;
                shift  += 5;
            } while (b >= 0x20);
            lat += (deltaLat & 1) != 0 ? ~(deltaLat >> 1) : (deltaLat >> 1);

            // Decode longitude delta
            shift = 0;
            int deltaLng = 0;
            do {
                b        = polyline.charAt(index++) - 63;
                deltaLng |= (b & 0x1f) << shift;
                shift    += 5;
            } while (b >= 0x20);
            lng += (deltaLng & 1) != 0 ? ~(deltaLng >> 1) : (deltaLng >> 1);

            result.add(new double[]{ lat / 1e5, lng / 1e5 });
        }
        return result;
    }

    /**
     * Calculates the total route distance of an encoded polyline in kilometres,
     * using the Haversine formula between consecutive decoded points.
     *
     * @param polyline Google Encoded Polyline string
     * @return distance in km (0.0 for empty or single-point polylines)
     */
    public static double calculateDistanceKm(String polyline) {
        List<double[]> points = decode(polyline);
        if (points.size() < 2) return 0.0;

        double total = 0.0;
        for (int i = 1; i < points.size(); i++) {
            total += haversineKm(points.get(i - 1)[0], points.get(i - 1)[1],
                                 points.get(i)[0],     points.get(i)[1]);
        }
        return total;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
