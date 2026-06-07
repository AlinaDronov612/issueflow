package com.att.tdp.issueflow.comment.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code PATCH /tickets/{ticketId}/comments/{commentId}}. */
public record UpdateCommentRequest(
        @NotBlank String content
) {
}
