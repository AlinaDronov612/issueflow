package com.att.tdp.issueflow.auth;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory deny-list of revoked token ids ({@code jti}). Logout adds the
 * current token's jti here; the auth filter rejects any token whose jti is
 * present. Note: this is per-instance and cleared on restart — acceptable given
 * tokens also expire on their own (documented in run.md).
 */
@Component
public class TokenDenyList {

    private final Set<String> revokedTokenIds = ConcurrentHashMap.newKeySet();

    public void revoke(String tokenId) {
        revokedTokenIds.add(tokenId);
    }

    public boolean isRevoked(String tokenId) {
        return revokedTokenIds.contains(tokenId);
    }
}
