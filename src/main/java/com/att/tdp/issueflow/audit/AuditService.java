package com.att.tdp.issueflow.audit;

import com.att.tdp.issueflow.audit.dto.AuditLogResponse;
import com.att.tdp.issueflow.auth.AuthPrincipal;
import com.att.tdp.issueflow.common.enums.Actor;
import com.att.tdp.issueflow.common.enums.AuditAction;
import com.att.tdp.issueflow.common.enums.AuditEntityType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes and reads the append-only audit log. Records join the caller's
 * transaction, so an action and its audit entry commit or roll back together.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final AuditMapper auditMapper;

    /**
     * Records a user-initiated action. {@code performedBy} is sourced from the
     * authenticated principal (open-Q #3); it is null if no JWT principal is present.
     */
    @Transactional
    public void record(AuditAction action, AuditEntityType entityType, Long entityId) {
        auditLogRepository.save(AuditLog.builder()
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .performedBy(currentPrincipalId())
                .actor(Actor.USER)
                .build());
    }

    /**
     * Records a system-initiated action (e.g. auto-assignment). {@code actor} is
     * SYSTEM and {@code performedBy} is null — there is no human behind it.
     */
    @Transactional
    public void recordSystem(AuditAction action, AuditEntityType entityType, Long entityId) {
        auditLogRepository.save(AuditLog.builder()
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .performedBy(null)
                .actor(Actor.SYSTEM)
                .build());
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAuditLogs(AuditEntityType entityType, Long entityId,
                                               AuditAction action, Actor actor) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (entityType != null) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
            }
            if (entityId != null) {
                predicates.add(cb.equal(root.get("entityId"), entityId));
            }
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (actor != null) {
                predicates.add(cb.equal(root.get("actor"), actor));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return auditLogRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "id")).stream()
                .map(auditMapper::toResponse)
                .toList();
    }

    private Long currentPrincipalId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
            return principal.id();
        }
        return null;
    }
}
