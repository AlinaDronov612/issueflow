package com.att.tdp.issueflow.auth;

import com.att.tdp.issueflow.common.enums.Role;

/**
 * The authenticated user extracted from a validated JWT. This is the principal
 * stored in the Spring Security context and is the authoritative identity for
 * all downstream actions (e.g. audit {@code performedBy}).
 */
public record AuthPrincipal(Long id, String username, Role role) {
}
