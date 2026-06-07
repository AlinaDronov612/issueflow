package com.att.tdp.issueflow.audit;

import com.att.tdp.issueflow.common.BaseEntity;
import com.att.tdp.issueflow.common.enums.Actor;
import com.att.tdp.issueflow.common.enums.AuditAction;
import com.att.tdp.issueflow.common.enums.AuditEntityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Append-only record of a state-changing action. Entries are only ever inserted
 * and read — never updated or deleted.
 *
 * <p>{@code performedBy} is a plain user id (not a FK relationship): an audit log
 * is immutable history and must survive deletion of the referenced user. It is
 * {@code null} for SYSTEM-initiated actions. The {@code timestamp} exposed by the
 * API is the row's creation time ({@code createdAt} from {@link BaseEntity}).
 */
@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuditEntityType entityType;

    @Column(nullable = false, updatable = false)
    private Long entityId;

    @Column(updatable = false)
    private Long performedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Actor actor;
}
