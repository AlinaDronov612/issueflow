package com.att.tdp.issueflow.workload.dto;

/**
 * One developer's open-ticket load within a project, as returned by
 * {@code GET /projects/{projectId}/workload}. Field names match the README.
 */
public record WorkloadResponse(
        Long userId,
        String username,
        long openTicketCount
) {
}
