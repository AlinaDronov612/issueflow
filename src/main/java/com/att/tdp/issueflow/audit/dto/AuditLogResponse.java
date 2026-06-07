package com.att.tdp.issueflow.audit.dto;

import com.att.tdp.issueflow.common.enums.Actor;
import com.att.tdp.issueflow.common.enums.AuditAction;
import com.att.tdp.issueflow.common.enums.AuditEntityType;

import java.time.Instant;

/** Response body for {@code GET /audit-logs}. */
public record AuditLogResponse(
        Long id,
        AuditAction action,
        AuditEntityType entityType,
        Long entityId,
        Long performedBy,
        Actor actor,
        Instant timestamp
) {
}
