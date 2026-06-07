package com.att.tdp.issueflow.comment;

import com.att.tdp.issueflow.comment.dto.CommentResponse;
import com.att.tdp.issueflow.comment.dto.MentionedUserResponse;
import com.att.tdp.issueflow.user.User;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** Maps {@link Comment} entities to response DTOs so entities never leak to the API. */
@Component
public class CommentMapper {

    public CommentResponse toResponse(Comment comment) {
        List<MentionedUserResponse> mentions = comment.getMentionedUsers().stream()
                .sorted(Comparator.comparing(User::getId))
                .map(u -> new MentionedUserResponse(u.getId(), u.getUsername(), u.getFullName()))
                .toList();
        return new CommentResponse(
                comment.getId(),
                comment.getTicket().getId(),
                comment.getAuthor().getId(),
                comment.getContent(),
                mentions
        );
    }
}
