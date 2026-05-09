package com.walkmate.infrastructure.walkpost;

/**
 * Port: abstract storage for route preview images.
 * The application layer depends on this interface; the Supabase implementation lives in infrastructure.
 */
public interface RoutePreviewStorage {

    /**
     * Uploads image bytes to the storage backend and returns the public URL.
     *
     * @param objectPath storage path, e.g. "walk-posts/{authorId}/{sessionId}/{postId}.png"
     * @param imageBytes raw PNG/JPEG bytes to upload
     * @return publicly accessible URL of the uploaded image
     * @throws RoutePreviewStorageException if the upload fails
     */
    String upload(String objectPath, byte[] imageBytes) throws RoutePreviewStorageException;

    /**
     * Deletes the image at the given storage path.
     * Implementations must not throw — failures should be logged internally.
     */
    void deleteQuietly(String objectPath);

    class RoutePreviewStorageException extends RuntimeException {
        public RoutePreviewStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
