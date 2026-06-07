package com.att.tdp.issueflow.project.dto;

/**
 * Request body for {@code PATCH /projects/{projectId}}. Both fields are optional
 * (partial update): a {@code null} field is left unchanged.
 */
public record UpdateProjectRequest(
        String name,
        String description
) {
}
