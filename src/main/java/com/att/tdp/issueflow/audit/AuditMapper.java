package com.att.tdp.issueflow.audit;

import com.att.tdp.issueflow.audit.dto.AuditLogResponse;
import org.springframework.stereotype.Component;

/** Maps {@link AuditLog} entities to response DTOs. */
@Component
public class AuditMapper {

    public AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getPerformedBy(),
                log.getActor(),
                log.getCreatedAt()
        );
    }
}
