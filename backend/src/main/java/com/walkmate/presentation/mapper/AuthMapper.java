package com.walkmate.presentation.mapper;

import com.walkmate.domain.user.User;
import com.walkmate.presentation.dto.response.ApiResponse;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {

    public ApiResponse toRegisterResponse(User user) {
        return ApiResponse.success("Register successful");
    }
}