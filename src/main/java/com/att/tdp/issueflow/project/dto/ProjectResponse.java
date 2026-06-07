package com.att.tdp.issueflow.project.dto;

/** Response body for project endpoints. The owner is exposed as {@code ownerId}. */
public record ProjectResponse(
        Long id,
        String name,
        String description,
        Long ownerId
) {
}
