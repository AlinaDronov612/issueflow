package com.att.tdp.issueflow.comment;

import com.att.tdp.issueflow.audit.AuditService;
import com.att.tdp.issueflow.auth.AuthPrincipal;
import com.att.tdp.issueflow.comment.dto.CommentResponse;
import com.att.tdp.issueflow.comment.dto.CreateCommentRequest;
import com.att.tdp.issueflow.comment.dto.MentionsPageResponse;
import com.att.tdp.issueflow.comment.dto.UpdateCommentRequest;
import com.att.tdp.issueflow.common.enums.AuditAction;
import com.att.tdp.issueflow.common.enums.AuditEntityType;
import com.att.tdp.issueflow.common.exception.BadRequestException;
import com.att.tdp.issueflow.common.exception.ForbiddenException;
import com.att.tdp.issueflow.common.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final CommentMapper commentMapper;
    private final MentionParser mentionParser;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<CommentResponse> getComments(Long ticketId) {
        requireTicket(ticketId);
        return commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(commentMapper::toResponse)
                .toList();
    }

    @Transactional
    public CommentResponse addComment(Long ticketId, CreateCommentRequest request, AuthPrincipal principal) {
        Ticket ticket = requireTicket(ticketId);

        // The authenticated principal is authoritative (open-Q #3).
        if (!request.authorId().equals(principal.id())) {
            throw new ForbiddenException("authorId must match the authenticated user");
        }
        User author = userRepository.findById(principal.id())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.id()));

        // Comments are allowed on a DONE ticket (open-Q #6): no status check here.
        Comment comment = Comment.builder()
                .ticket(ticket)
                .author(author)
                .content(request.content())
                .build();
        comment.getMentionedUsers().addAll(resolveMentions(request.content()));
        Comment saved = commentRepository.save(comment);
        auditService.record(AuditAction.CREATE, AuditEntityType.COMMENT, saved.getId());
        return commentMapper.toResponse(saved);
    }

    @Transactional
    public void updateComment(Long ticketId, Long commentId, UpdateCommentRequest request) {
        Comment comment = requireComment(ticketId, commentId);
        comment.setContent(request.content());
        // Re-evaluate mentions: replace the set so new ones are added and stale ones removed.
        comment.getMentionedUsers().clear();
        comment.getMentionedUsers().addAll(resolveMentions(request.content()));
        commentRepository.save(comment);
        auditService.record(AuditAction.UPDATE, AuditEntityType.COMMENT, commentId);
    }

    @Transactional(readOnly = true)
    public MentionsPageResponse getMentionsForUser(Long userId, int page, int pageSize) {
        if (page < 1) {
            throw new BadRequestException("page must be >= 1");
        }
        if (pageSize < 1) {
            throw new BadRequestException("pageSize must be >= 1");
        }
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<Comment> result =
                commentRepository.findByMentionedUsersIdOrderByCreatedAtDescIdDesc(userId, pageable);
        List<CommentResponse> data = result.getContent().stream()
                .map(commentMapper::toResponse)
                .toList();
        return new MentionsPageResponse(data, result.getTotalElements(), page);
    }

    /** Resolve {@code @username} tokens to existing users (case-insensitive, deduped). */
    private List<User> resolveMentions(String content) {
        Set<String> usernames = mentionParser.parse(content);
        if (usernames.isEmpty()) {
            return List.of();
        }
        return userRepository.findByUsernameLowerIn(usernames);
    }

    @Transactional
    public void deleteComment(Long ticketId, Long commentId) {
        Comment comment = requireComment(ticketId, commentId);
        commentRepository.delete(comment);
        auditService.record(AuditAction.DELETE, AuditEntityType.COMMENT, commentId);
    }

    /**
     * Resolves an active (non-soft-deleted) ticket, consistent with the other
     * ticket sub-resources (attachments, dependencies). A soft-deleted ticket is
     * hidden from standard responses, so its comments 404 too — and never leak
     * through {@code /users/:id/mentions}.
     */
    private Ticket requireTicket(Long ticketId) {
        return ticketRepository.findByIdAndDeletedFalse(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
    }

    private Comment requireComment(Long ticketId, Long commentId) {
        // 404 if the parent ticket is missing or soft-deleted, before touching the comment.
        requireTicket(ticketId);
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));
        if (!comment.getTicket().getId().equals(ticketId)) {
            throw new ResourceNotFoundException(
                    "Comment " + commentId + " not found on ticket " + ticketId);
        }
        return comment;
    }
}
