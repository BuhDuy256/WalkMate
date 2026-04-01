package com.walkmate.infrastructure.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Stores avatar images on the local filesystem and returns the public URL.
 *
 * Directory: ${app.file.upload-dir}/avatars/
 * Served by:  GET /api/v1/files/avatars/{filename}
 *
 * Swap this bean for an S3/MinIO implementation in production by replacing
 * the body of store() and delete() while keeping the interface identical.
 */
@Service
public class AvatarStorageService {

    private final Path avatarDir;
    private final String publicBaseUrl;

    public AvatarStorageService(
            @Value("${app.file.upload-dir:./uploads}") String uploadDir,
            @Value("${app.file.public-base-url:http://localhost:8080}") String publicBaseUrl) throws IOException {
        this.avatarDir     = Paths.get(uploadDir, "avatars").toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl;
        Files.createDirectories(this.avatarDir);
    }

    /**
     * Persists the uploaded file and returns its public-facing URL.
     *
     * Filename strategy: {userId}_{random-uuid}.{ext} so each upload is unique
     * and old URLs remain valid until explicitly cleaned up.
     */
    public String store(UUID userId, MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename();
        String ext = (originalName != null && originalName.contains("."))
                ? originalName.substring(originalName.lastIndexOf('.'))
                : ".jpg";

        // Sanitise extension to alphanumeric only
        ext = ext.replaceAll("[^a-zA-Z0-9.]", "");

        String filename = userId + "_" + UUID.randomUUID() + ext;
        Path target = avatarDir.resolve(filename);

        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        return publicBaseUrl + "/api/v1/files/avatars/" + filename;
    }

    /** Returns the absolute path for a given filename so it can be served. */
    public Path resolve(String filename) {
        // Prevent path traversal
        Path resolved = avatarDir.resolve(filename).normalize();
        if (!resolved.startsWith(avatarDir)) {
            throw new IllegalArgumentException("Invalid filename");
        }
        return resolved;
    }
}
