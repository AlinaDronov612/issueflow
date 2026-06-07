package com.att.tdp.issueflow.dependency.dto;

import jakarta.validation.constraints.NotNull;

/** Request body for {@code POST /tickets/{ticketId}/dependencies}: the blocker ticket id. */
public record AddDependencyRequest(
        @NotNull Long blockedBy
) {
}
