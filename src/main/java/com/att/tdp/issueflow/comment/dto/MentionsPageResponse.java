package com.att.tdp.issueflow.comment.dto;

import java.util.List;

/**
 * Paginated response for {@code GET /users/{userId}/mentions}. Field names and
 * shape match the README: {@code data} (the comments), {@code total} (overall
 * match count), {@code page} (the 1-based page returned).
 */
public record MentionsPageResponse(
        List<CommentResponse> data,
        long total,
        int page
) {
}
