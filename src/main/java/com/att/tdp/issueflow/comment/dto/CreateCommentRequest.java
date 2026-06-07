package com.att.tdp.issueflow.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /tickets/{ticketId}/comments}. The {@code authorId}
 * must match the authenticated principal (open-Q #3).
 */
public record CreateCommentRequest(
        @NotNull Long authorId,
        @NotBlank String content
) {
}
