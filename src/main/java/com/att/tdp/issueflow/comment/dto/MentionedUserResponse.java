package com.att.tdp.issueflow.comment.dto;

/** A user referenced by an {@code @username} mention in a comment. */
public record MentionedUserResponse(
        Long id,
        String username,
        String fullName
) {
}
