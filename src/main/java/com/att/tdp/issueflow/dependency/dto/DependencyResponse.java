package com.att.tdp.issueflow.dependency.dto;

import com.att.tdp.issueflow.common.enums.Status;

/** A blocker ticket, as returned by {@code GET /tickets/{ticketId}/dependencies}. */
public record DependencyResponse(
        Long id,
        String title,
        Status status
) {
}
