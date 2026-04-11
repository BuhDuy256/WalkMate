package com.walkmate.data.repository;

import com.walkmate.data.datasource.remote.api.ProfileApiService;
import com.walkmate.data.datasource.remote.dto.ProfileAvatarUploadResponseDto;
import com.walkmate.data.datasource.remote.dto.ProfileResponseDto;
import com.walkmate.data.datasource.remote.dto.ProfileSetupAckResponseDto;
import com.walkmate.data.datasource.remote.dto.SetupProfileRequestDto;
import com.walkmate.data.mapper.ProfileDomainToDtoMapper;
import com.walkmate.data.mapper.ProfileDtoToDomainMapper;
import com.walkmate.domain.profile.ProfileAvatarUpload;
import com.walkmate.domain.profile.Profile;
import com.walkmate.domain.profile.ProfileErrorCode;
import com.walkmate.domain.profile.ProfileException;
import com.walkmate.domain.profile.ProfileRepository;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.util.UUID;

public class ProfileRepositoryImpl implements ProfileRepository {
    private final ProfileApiService apiService;
    private final ProfileDomainToDtoMapper domainToDtoMapper;
    private final ProfileDtoToDomainMapper dtoToDomainMapper;

    public ProfileRepositoryImpl(
            ProfileApiService apiService,
            ProfileDomainToDtoMapper domainToDtoMapper,
            ProfileDtoToDomainMapper dtoToDomainMapper
    ) {
        this.apiService = apiService;
        this.domainToDtoMapper = domainToDtoMapper;
        this.dtoToDomainMapper = dtoToDomainMapper;
    }

    @Override
    public String uploadAvatar(UUID userId, ProfileAvatarUpload avatarUpload) throws ProfileException {
        String mimeType = avatarUpload.getMimeType() == null || avatarUpload.getMimeType().trim().isEmpty()
                ? "image/*"
                : avatarUpload.getMimeType();
        RequestBody requestBody = RequestBody.create(MediaType.parse(mimeType), avatarUpload.getBytes());
        MultipartBody.Part filePart = MultipartBody.Part.createFormData(
                "file",
                avatarUpload.getFileName(),
                requestBody
        );
        Call<ProfileAvatarUploadResponseDto> call = apiService.uploadAvatar(userId, filePart);

        try {
            Response<ProfileAvatarUploadResponseDto> response = call.execute();
            if (response.isSuccessful() && response.body() != null && response.body().getAvatarUrl() != null
                    && !response.body().getAvatarUrl().trim().isEmpty()) {
                return response.body().getAvatarUrl();
            }

            String errorBody = response.errorBody() != null ? response.errorBody().string() : "";
            ProfileErrorCode code = mapHttpErrorToProfileError(response.code(), errorBody);
            throw new ProfileException(code, "Failed to upload avatar: " + errorBody);
        } catch (IOException e) {
            throw new ProfileException(ProfileErrorCode.NETWORK_ERROR, "Network error", e);
        }
    }

    @Override
    public Profile setupProfile(Profile profile) throws ProfileException {
        SetupProfileRequestDto requestDto = domainToDtoMapper.mapToDto(profile);
        Call<ProfileSetupAckResponseDto> call = apiService.setupProfile(profile.getUserId(), requestDto);

        try {
            Response<ProfileSetupAckResponseDto> response = call.execute();
            if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                return profile;
            }

            String errorBody = response.errorBody() != null ? response.errorBody().string() : "";
            ProfileErrorCode code = mapHttpErrorToProfileError(response.code(), errorBody);
            throw new ProfileException(code, "Failed to save profile: " + errorBody);
        } catch (IOException e) {
            throw new ProfileException(ProfileErrorCode.NETWORK_ERROR, "Network error", e);
        }
    }

    @Override
    public Profile getProfile(UUID userId, UUID viewerId) throws ProfileException {
        Call<ProfileResponseDto> call = apiService.getProfile(userId, viewerId);
        try {
            Response<ProfileResponseDto> response = call.execute();
            if (response.isSuccessful() && response.body() != null) {
                return dtoToDomainMapper.mapToDomain(response.body());
            }

            String errorBody = response.errorBody() != null ? response.errorBody().string() : "";
            ProfileErrorCode code = mapHttpErrorToProfileError(response.code(), errorBody);
            throw new ProfileException(code, "Failed to load profile: " + errorBody);
        } catch (IOException e) {
            throw new ProfileException(ProfileErrorCode.NETWORK_ERROR, "Network error", e);
        }
    }

    private ProfileErrorCode mapHttpErrorToProfileError(int httpCode, String errorBody) {
        if (errorBody.contains("PROFILE_NOT_FOUND")) {
            return ProfileErrorCode.PROFILE_NOT_FOUND;
        }
        if (errorBody.contains("PROFILE_PRIVATE")) {
            return ProfileErrorCode.PROFILE_PRIVATE;
        }
        if (errorBody.contains("PROFILE_INVALID_NAME")) {
            return ProfileErrorCode.PROFILE_INVALID_NAME;
        }
        if (errorBody.contains("PROFILE_INVALID_MODE")) {
            return ProfileErrorCode.PROFILE_INVALID_MODE;
        }
        if (errorBody.contains("PROFILE_INVALID_TAG")) {
            return ProfileErrorCode.PROFILE_INVALID_TAG;
        }
        if (errorBody.contains("PROFILE_TAGS_MIN_REQUIRED")) {
            return ProfileErrorCode.PROFILE_TAGS_MIN_REQUIRED;
        }
        if (httpCode == 403) {
            return ProfileErrorCode.PROFILE_PRIVATE;
        }
        return ProfileErrorCode.UNKNOWN_ERROR;
    }
}
