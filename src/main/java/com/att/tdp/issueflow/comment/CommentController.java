package com.att.tdp.issueflow.comment;

import com.att.tdp.issueflow.auth.AuthPrincipal;
import com.att.tdp.issueflow.comment.dto.CommentResponse;
import com.att.tdp.issueflow.comment.dto.CreateCommentRequest;
import com.att.tdp.issueflow.comment.dto.UpdateCommentRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Comment endpoints nested under a ticket. Paths/methods match the README
 * (200 OK on create, PATCH with empty body on update).
 */
@Tag(name = "Comments", description = "Comments on a ticket, including @mention resolution")
@RestController
@RequestMapping("/tickets/{ticketId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    public List<CommentResponse> getComments(@PathVariable Long ticketId) {
        return commentService.getComments(ticketId);
    }

    @PostMapping
    public CommentResponse addComment(@PathVariable Long ticketId,
                                      @Valid @RequestBody CreateCommentRequest request,
                                      @AuthenticationPrincipal AuthPrincipal principal) {
        return commentService.addComment(ticketId, request, principal);
    }

    @PatchMapping("/{commentId}")
    public void updateComment(@PathVariable Long ticketId,
                              @PathVariable Long commentId,
                              @Valid @RequestBody UpdateCommentRequest request) {
        commentService.updateComment(ticketId, commentId, request);
    }

    @DeleteMapping("/{commentId}")
    public void deleteComment(@PathVariable Long ticketId, @PathVariable Long commentId) {
        commentService.deleteComment(ticketId, commentId);
    }
}
