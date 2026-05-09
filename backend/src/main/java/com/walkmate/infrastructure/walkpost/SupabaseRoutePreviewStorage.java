package com.walkmate.infrastructure.walkpost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Uploads and deletes route preview images using the Supabase Storage REST API.
 *
 * Required environment variables:
 *   SUPABASE_URL                  — e.g. https://abcxyz.supabase.co
 *   SUPABASE_SERVICE_ROLE_KEY     — service role key (server-side only, never expose to clients)
 *   SUPABASE_ROUTE_PREVIEW_BUCKET — bucket name, e.g. "walk-route-previews"
 */
@Component
public class SupabaseRoutePreviewStorage implements RoutePreviewStorage {

    private static final Logger log = LoggerFactory.getLogger(SupabaseRoutePreviewStorage.class);

    private final String supabaseUrl;
    private final String serviceRoleKey;
    private final String bucket;
    private final HttpClient httpClient;

    public SupabaseRoutePreviewStorage(
            @Value("${app.supabase.url}") String supabaseUrl,
            @Value("${app.supabase.service-role-key}") String serviceRoleKey,
            @Value("${app.supabase.route-preview-bucket}") String bucket) {
        this.supabaseUrl    = supabaseUrl.endsWith("/") ? supabaseUrl.substring(0, supabaseUrl.length() - 1) : supabaseUrl;
        this.serviceRoleKey = serviceRoleKey;
        this.bucket         = bucket;
        this.httpClient     = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String upload(String objectPath, byte[] imageBytes) throws RoutePreviewStorageException {
        if (supabaseUrl.isBlank() || serviceRoleKey.isBlank()) {
            throw new RoutePreviewStorageException(
                    "SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY are not configured — upload skipped", null);
        }
        String uploadUrl = supabaseUrl + "/storage/v1/object/" + bucket + "/" + objectPath;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + serviceRoleKey)
                .header("Content-Type", "image/png")
                .header("x-upsert", "true")
                .POST(HttpRequest.BodyPublishers.ofByteArray(imageBytes))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RoutePreviewStorageException(
                        "Supabase upload failed — HTTP " + response.statusCode() + ": " + response.body(), null);
            }
            return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + objectPath;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RoutePreviewStorageException("Supabase upload failed for path: " + objectPath, e);
        }
    }

    @Override
    public void deleteQuietly(String objectPath) {
        if (objectPath == null || objectPath.isBlank()) return;
        String deleteUrl = supabaseUrl + "/storage/v1/object/" + bucket + "/" + objectPath;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(deleteUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + serviceRoleKey)
                .DELETE()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Supabase delete returned {} for path {}: {}", response.statusCode(), objectPath, response.body());
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Supabase delete failed for path {}: {}", objectPath, e.getMessage());
        }
    }
}
