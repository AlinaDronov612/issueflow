package com.att.tdp.issueflow.audit;

import com.att.tdp.issueflow.audit.dto.AuditLogResponse;
import com.att.tdp.issueflow.common.enums.Actor;
import com.att.tdp.issueflow.common.enums.AuditAction;
import com.att.tdp.issueflow.common.enums.AuditEntityType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only audit log endpoint. All filters are optional; combining them ANDs
 * the conditions.
 */
@RestController
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditService auditService;

    @GetMapping("/audit-logs")
    public List<AuditLogResponse> getAuditLogs(
            @RequestParam(required = false) AuditEntityType entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) Actor actor) {
        return auditService.getAuditLogs(entityType, entityId, action, actor);
    }
}
