package com.wonhoone.misterworld.api.dto;

import com.wonhoone.misterworld.domain.UserRole;
import com.wonhoone.misterworld.infrastructure.persistence.entity.UserAccountJpaEntity;

public record UserResponse(long id, UserRole role, String name) {
    public static UserResponse from(UserAccountJpaEntity account) {
        return new UserResponse(account.getId(), account.getRole(), account.getName());
    }
}
