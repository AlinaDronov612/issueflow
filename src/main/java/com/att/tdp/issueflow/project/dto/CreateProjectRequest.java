package com.att.tdp.issueflow.project.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /projects}. {@code ownerId} is accepted to match
 * the README shape, but the owner is set from the authenticated principal; a
 * supplied {@code ownerId} that differs from the caller is rejected (403).
 */
public record CreateProjectRequest(
        @NotBlank String name,
        String description,
        Long ownerId
) {
}
