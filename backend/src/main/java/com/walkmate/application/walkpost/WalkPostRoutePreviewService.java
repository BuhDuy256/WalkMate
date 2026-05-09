package com.walkmate.application.walkpost;

import com.walkmate.domain.tracking.TrackingChunkRepository;
import com.walkmate.domain.walkpost.RoutePreviewStatus;
import com.walkmate.domain.walkpost.WalkPostRepository;
import com.walkmate.infrastructure.walkpost.RoutePreviewStorage;
import com.walkmate.infrastructure.walkpost.StaticMapImageClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates server-side route preview generation for a walk post.
 *
 * Flow:
 *   1. Fetch the author's GPS polylines from session_point_chunks.
 *   2. If none → mark NO_ROUTE and return.
 *   3. Build a Google Static Maps request with the route polyline overlay.
 *   4. Download the resulting PNG image.
 *   5. Upload the image bytes to Supabase Storage.
 *   6. Persist the public URL, storage path, and READY status on walk_post.
 *
 * Failures are caught and logged. Post creation always succeeds; on any external
 * API failure the post is marked FAILED (routePreviewUrl stays null) rather than
 * rolling back the post row.
 *
 * TRANSACTION NOTE: This service must be called AFTER the post-creation transaction
 * has committed. Calling generateAndPersist() while the walk_post INSERT is still
 * in an open transaction would cause a deadlock when the route preview UPDATE tries
 * to lock the same row. The controller sequences: createPost() (TX1 commits) →
 * generateAndPersist() (separate DB calls for the UPDATE) → re-fetch post.
 */
@Service
@RequiredArgsConstructor
public class WalkPostRoutePreviewService {

    private static final Logger log = LoggerFactory.getLogger(WalkPostRoutePreviewService.class);

    private final TrackingChunkRepository chunkRepository;
    private final StaticMapImageClient    staticMapClient;
    private final RoutePreviewStorage     previewStorage;
    private final WalkPostRepository      walkPostRepository;

    /**
     * Generates a route preview image for the given post and persists the result.
     * Must be called AFTER the post-creation transaction has committed.
     *
     * @param postId    the newly created walk post ID
     * @param sessionId the walk session ID (used to query GPS chunks)
     * @param authorId  the author/participant whose route to render
     */
    public void generateAndPersist(String postId, String sessionId, String authorId) {
        try {
            List<String> polylines = chunkRepository.findPolylinesBySessionAndUser(sessionId, authorId);
            if (polylines.isEmpty()) {
                log.debug("No GPS chunks for session={} author={}. Marking NO_ROUTE.", sessionId, authorId);
                walkPostRepository.updateRoutePreview(postId, null, null, RoutePreviewStatus.NO_ROUTE.name());
                return;
            }

            byte[] imageBytes = staticMapClient.downloadRoutePreview(polylines);
            if (imageBytes == null || imageBytes.length == 0) {
                log.debug("Static map returned empty bytes for post={}. Marking NO_ROUTE.", postId);
                walkPostRepository.updateRoutePreview(postId, null, null, RoutePreviewStatus.NO_ROUTE.name());
                return;
            }

            String storagePath = "walk-posts/" + authorId + "/" + sessionId + "/" + postId + ".png";
            String publicUrl   = previewStorage.upload(storagePath, imageBytes);

            walkPostRepository.updateRoutePreview(postId, publicUrl, storagePath, RoutePreviewStatus.READY.name());
            log.info("Route preview READY for post={} url={}", postId, publicUrl);

        } catch (StaticMapImageClient.StaticMapException e) {
            log.warn("Static map failed for post={}: {}", postId, e.getMessage());
            safeMarkFailed(postId);
        } catch (RoutePreviewStorage.RoutePreviewStorageException e) {
            log.warn("Supabase upload failed for post={}: {}", postId, e.getMessage());
            safeMarkFailed(postId);
        } catch (Exception e) {
            log.error("Unexpected error generating route preview for post={}", postId, e);
            safeMarkFailed(postId);
        }
    }

    /**
     * Deletes the stored route preview image from Supabase Storage.
     * Failures are logged and silently swallowed — caller's main operation continues.
     */
    public void deleteStorageImageQuietly(String routePreviewPath) {
        if (routePreviewPath == null || routePreviewPath.isBlank()) return;
        previewStorage.deleteQuietly(routePreviewPath);
    }

    private void safeMarkFailed(String postId) {
        try {
            walkPostRepository.updateRoutePreview(postId, null, null, RoutePreviewStatus.FAILED.name());
        } catch (Exception ex) {
            log.error("Could not mark post {} as FAILED after preview error", postId, ex);
        }
    }
}
