package com.att.tdp.issueflow.user.dto;

import com.att.tdp.issueflow.common.enums.Role;

/**
 * Request body for {@code POST /users/update/{userId}}. Both fields are optional
 * (partial update): a {@code null} field is left unchanged.
 */
public record UpdateUserRequest(
        String fullName,
        Role role
) {
}
