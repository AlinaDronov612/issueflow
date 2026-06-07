package com.att.tdp.issueflow.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /auth/login}. */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}
