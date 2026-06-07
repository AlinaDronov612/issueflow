package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.common.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByProjectIdAndDeletedFalse(Long projectId);

    List<Ticket> findByProjectIdAndDeletedTrue(Long projectId);

    Optional<Ticket> findByIdAndDeletedFalse(Long id);

    boolean existsByAssigneeId(Long assigneeId);

    /**
     * A developer's open workload in a project: assigned, not soft-deleted, and
     * not in the given (resolved) status. Used for auto-assignment and the
     * workload endpoint (pass {@code Status.DONE}).
     */
    long countByAssigneeIdAndProjectIdAndDeletedFalseAndStatusNot(
            Long assigneeId, Long projectId, Status status);

    /**
     * Tickets eligible for auto-escalation: active, with a dueDate that is in the
     * past, and not yet resolved (the passed {@code done} status is excluded).
     */
    @Query("""
            select t from Ticket t
            where t.deleted = false
              and t.dueDate is not null
              and t.dueDate < :now
              and t.status <> :done
            """)
    List<Ticket> findEscalationCandidates(@Param("now") Instant now, @Param("done") Status done);
}
