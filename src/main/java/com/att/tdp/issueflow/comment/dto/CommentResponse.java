package com.att.tdp.issueflow.comment.dto;

import java.util.List;

/**
 * Response body for comment endpoints. {@code mentionedUsers} is part of the
 * contract shape but stays empty until the mentions phase.
 */
public record CommentResponse(
        Long id,
        Long ticketId,
        Long authorId,
        String content,
        List<MentionedUserResponse> mentionedUsers
) {
}
