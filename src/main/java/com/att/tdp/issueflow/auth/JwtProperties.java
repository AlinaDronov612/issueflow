package com.att.tdp.issueflow.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT configuration bound from {@code app.jwt.*}.
 *
 * @param secret            HMAC signing secret (HS256 requires >= 32 bytes)
 * @param expirationSeconds token lifetime in seconds
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expirationSeconds) {
}
