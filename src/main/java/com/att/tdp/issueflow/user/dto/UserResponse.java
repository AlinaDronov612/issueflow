package com.att.tdp.issueflow.user.dto;

import com.att.tdp.issueflow.common.enums.Role;

/** Response body for user endpoints. Never includes the password hash. */
public record UserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        Role role
) {
}
