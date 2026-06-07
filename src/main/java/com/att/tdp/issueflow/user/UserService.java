package com.att.tdp.issueflow.user;

import com.att.tdp.issueflow.audit.AuditLogRepository;
import com.att.tdp.issueflow.audit.AuditService;
import com.att.tdp.issueflow.comment.CommentRepository;
import com.att.tdp.issueflow.common.enums.AuditAction;
import com.att.tdp.issueflow.common.enums.AuditEntityType;
import com.att.tdp.issueflow.common.exception.BadRequestException;
import com.att.tdp.issueflow.common.exception.ConflictException;
import com.att.tdp.issueflow.common.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.user.dto.CreateUserRequest;
import com.att.tdp.issueflow.user.dto.UpdateUserRequest;
import com.att.tdp.issueflow.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final TicketRepository ticketRepository;
    private final CommentRepository commentRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(userMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId) {
        return userMapper.toResponse(findByIdOrThrow(userId));
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("username '" + request.username() + "' is already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("email '" + request.email() + "' is already registered");
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .fullName(request.fullName())
                .role(request.role())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();

        User saved = userRepository.save(user);
        auditService.record(AuditAction.CREATE, AuditEntityType.USER, saved.getId());
        return userMapper.toResponse(saved);
    }

    @Transactional
    public void updateUser(Long userId, UpdateUserRequest request) {
        User user = findByIdOrThrow(userId);
        if (request.fullName() != null) {
            if (request.fullName().isBlank()) {
                throw new BadRequestException("fullName must not be blank");
            }
            user.setFullName(request.fullName());
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        userRepository.save(user);
        auditService.record(AuditAction.UPDATE, AuditEntityType.USER, userId);
    }

    /**
     * Hard-deletes a user (the README exposes no soft delete for users). Per
     * open-Q #5, rejects with 409 if the user is still referenced by any ticket
     * (assignee), comment (author), or audit log (performedBy).
     */
    @Transactional
    public void deleteUser(Long userId) {
        User user = findByIdOrThrow(userId);
        if (ticketRepository.existsByAssigneeId(userId)) {
            throw new ConflictException("user " + userId + " is assigned to tickets and cannot be deleted");
        }
        if (commentRepository.existsByAuthorId(userId)) {
            throw new ConflictException("user " + userId + " authored comments and cannot be deleted");
        }
        if (auditLogRepository.existsByPerformedBy(userId)) {
            throw new ConflictException("user " + userId + " is referenced by audit logs and cannot be deleted");
        }
        userRepository.delete(user);
        auditService.record(AuditAction.DELETE, AuditEntityType.USER, userId);
    }

    private User findByIdOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
