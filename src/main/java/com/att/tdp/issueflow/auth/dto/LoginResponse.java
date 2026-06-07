package com.att.tdp.issueflow.auth.dto;

/** Response body for {@code POST /auth/login}: { accessToken, tokenType, expiresIn }. */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
