package com.walkmate.data.repository;

import com.walkmate.data.datasource.remote.api.ProfileApiService;
import com.walkmate.data.datasource.remote.dto.ProfileResponseDto;
import com.walkmate.data.datasource.remote.dto.SetupProfileRequestDto;
import com.walkmate.data.mapper.ProfileDomainToDtoMapper;
import com.walkmate.data.mapper.ProfileDtoToDomainMapper;
import com.walkmate.domain.profile.Profile;
import com.walkmate.domain.profile.ProfileErrorCode;
import com.walkmate.domain.profile.ProfileException;
import com.walkmate.domain.profile.ProfileRepository;
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
    public Profile setupProfile(Profile profile) throws ProfileException {
        SetupProfileRequestDto requestDto = domainToDtoMapper.mapToDto(profile);
        Call<ProfileResponseDto> call = apiService.setupProfile(requestDto);

        try {
            Response<ProfileResponseDto> response = call.execute();
            if (response.isSuccessful() && response.body() != null) {
                return dtoToDomainMapper.mapToDomain(response.body());
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
