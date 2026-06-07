package com.att.tdp.issueflow.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Append-only: only save (insert) and read are ever used — never update/delete. */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>,
        JpaSpecificationExecutor<AuditLog> {

    boolean existsByPerformedBy(Long performedBy);
}
