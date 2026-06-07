package com.att.tdp.issueflow.user.dto;

import com.att.tdp.issueflow.common.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /users}. The {@code password} field is a
 * deliberate deviation from the README contract (see {@link com.att.tdp.issueflow.user.User}).
 */
public record CreateUserRequest(
        @NotBlank String username,
        @NotBlank @Email String email,
        @NotBlank String fullName,
        @NotNull Role role,
        @NotBlank @Size(min = 6, max = 100) String password
) {
}
