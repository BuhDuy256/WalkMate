package com.walkmate.infrastructure.walkpost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Downloads a static route preview image from Google Static Maps API.
 *
 * Takes a list of Google Encoded Polyline strings (from session_point_chunks),
 * decodes them, downsamples to ≤150 points, re-encodes as a single polyline,
 * and fetches a PNG image via the Static Maps API.
 *
 * Required environment variable:
 *   MAP_PROVIDER_API_KEY — Google Maps API key with Static Maps enabled
 */
@Component
public class StaticMapImageClient {

    private static final Logger log = LoggerFactory.getLogger(StaticMapImageClient.class);
    private static final int MAX_POINTS = 150;
    private static final String STATIC_MAPS_BASE = "https://maps.googleapis.com/maps/api/staticmap";

    private final String apiKey;
    private final HttpClient httpClient;

    public StaticMapImageClient(@Value("${app.map.provider-api-key}") String apiKey) {
        this.apiKey    = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Downloads a PNG route preview image.
     *
     * @param encodedPolylines list of Google Encoded Polyline strings from session_point_chunks
     * @return PNG image bytes, or null if the list is empty
     * @throws StaticMapException if the API key is not configured or the provider returns an error
     */
    public byte[] downloadRoutePreview(List<String> encodedPolylines) throws StaticMapException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new StaticMapException(
                    "MAP_PROVIDER_API_KEY is not configured — route preview generation skipped");
        }
        if (encodedPolylines == null || encodedPolylines.isEmpty()) {
            return null;
        }

        List<double[]> allPoints = new ArrayList<>();
        for (String poly : encodedPolylines) {
            if (poly != null && !poly.isEmpty()) {
                allPoints.addAll(decodePolyline(poly));
            }
        }
        if (allPoints.isEmpty()) {
            return null;
        }

        List<double[]> sampled = downsample(allPoints, MAX_POINTS);
        String reEncoded = encodePolyline(sampled);

        String url = buildUrl(reEncoded);
        log.debug("Fetching static map: {} points, URL length={}", sampled.size(), url.length());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new StaticMapException("Static Maps API returned HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StaticMapException("Static Maps API request failed: " + e.getMessage(), e);
        }
    }

    private String buildUrl(String encodedPolyline) {
        // Route color: WalkMate orange #F97316, full opacity
        String pathStyle = "color:0xF97316FF|weight:4|enc:" + URLEncoder.encode(encodedPolyline, StandardCharsets.UTF_8);
        return STATIC_MAPS_BASE
                + "?size=600x300"
                + "&maptype=roadmap"
                + "&path=" + pathStyle
                + "&key=" + apiKey;
    }

    // ── Google Encoded Polyline codec ─────────────────────────────────────────

    /** Decodes a Google Encoded Polyline string to a list of [lat, lng] pairs. */
    static List<double[]> decodePolyline(String encoded) {
        List<double[]> points = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;
        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
            lat += dlat;

            shift = 0; result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
            lng += dlng;

            points.add(new double[]{lat / 1e5, lng / 1e5});
        }
        return points;
    }

    /** Re-encodes a list of [lat, lng] pairs as a Google Encoded Polyline string. */
    static String encodePolyline(List<double[]> points) {
        StringBuilder sb = new StringBuilder();
        int prevLat = 0, prevLng = 0;
        for (double[] pt : points) {
            int lat = (int) Math.round(pt[0] * 1e5);
            int lng = (int) Math.round(pt[1] * 1e5);
            sb.append(encodeValue(lat - prevLat));
            sb.append(encodeValue(lng - prevLng));
            prevLat = lat;
            prevLng = lng;
        }
        return sb.toString();
    }

    private static String encodeValue(int value) {
        value = value < 0 ? ~(value << 1) : (value << 1);
        StringBuilder chunk = new StringBuilder();
        while (value >= 0x20) {
            chunk.append((char) ((0x20 | (value & 0x1f)) + 63));
            value >>= 5;
        }
        chunk.append((char) (value + 63));
        return chunk.toString();
    }

    /** Reduces a point list to at most maxPoints using uniform sampling. */
    static List<double[]> downsample(List<double[]> points, int maxPoints) {
        if (points.size() <= maxPoints) return points;
        List<double[]> result = new ArrayList<>(maxPoints);
        double step = (double) (points.size() - 1) / (maxPoints - 1);
        for (int i = 0; i < maxPoints; i++) {
            result.add(points.get((int) Math.round(i * step)));
        }
        return result;
    }

    public static class StaticMapException extends Exception {
        public StaticMapException(String message) { super(message); }
        public StaticMapException(String message, Throwable cause) { super(message, cause); }
    }
}
