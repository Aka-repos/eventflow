package com.eventflow.modules.identity.api;

import com.eventflow.modules.identity.api.dto.AuthDtos.AuthTokensResponse;
import com.eventflow.modules.identity.api.dto.AuthDtos.UserProfileDto;
import com.eventflow.modules.identity.application.result.AuthResult;
import com.eventflow.modules.identity.domain.User;
import org.springframework.stereotype.Component;

@Component
class AuthApiMapper {

    AuthTokensResponse toResponse(AuthResult result) {
        return new AuthTokensResponse(result.accessToken(), result.expiresInSeconds(),
                result.refreshToken(), toProfile(result.user()));
    }

    UserProfileDto toProfile(User user) {
        return new UserProfileDto(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.sortedRoleCodes(), user.getCreatedAt());
    }
}
