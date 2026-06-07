package com.att.tdp.issueflow.comment;

import com.att.tdp.issueflow.comment.dto.MentionsPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lists the comments in which a user is mentioned, per the README
 * ({@code GET /users/{userId}/mentions}). Paging is 1-based; defaults are
 * {@code page=1}, {@code pageSize=20}.
 */
@RestController
@RequiredArgsConstructor
public class MentionController {

    private final CommentService commentService;

    @GetMapping("/users/{userId}/mentions")
    public MentionsPageResponse getMentions(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return commentService.getMentionsForUser(userId, page, pageSize);
    }
}
