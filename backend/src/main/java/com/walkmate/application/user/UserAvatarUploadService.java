package com.walkmate.application.user;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
public class UserAvatarUploadService {
    private static final long MAX_AVATAR_SIZE_BYTES = 10L * 1024L * 1024L;

    private final Cloudinary cloudinary;
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;

    public UserAvatarUploadService(
            Cloudinary cloudinary,
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret
    ) {
        this.cloudinary = cloudinary;
        this.cloudName = cloudName;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    public String uploadAvatar(UUID userId, MultipartFile file) {
        validateCloudinaryConfig();
        validateFile(file);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", "walkmate/profile-avatars",
                            "public_id", userId.toString(),
                            "overwrite", true,
                            "invalidate", true,
                            "resource_type", "image"
                    )
            );

            Object secureUrl = uploadResult.get("secure_url");
            if (!(secureUrl instanceof String) || ((String) secureUrl).isBlank()) {
                throw new IllegalStateException("Cloudinary did not return a secure URL");
            }
            return (String) secureUrl;
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload avatar", e);
        }
    }

    private void validateCloudinaryConfig() {
        if (isBlank(cloudName) || isBlank(apiKey) || isBlank(apiSecret)) {
            throw new IllegalStateException("Cloudinary is not configured");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Avatar file is required");
        }
        if (file.getSize() > MAX_AVATAR_SIZE_BYTES) {
            throw new IllegalArgumentException("Avatar must be 10 MB or smaller");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new IllegalArgumentException("Avatar must be an image");
        }
    }

    private boolean isBlank(Object value) {
        return value == null || value.toString().trim().isEmpty();
    }
}
