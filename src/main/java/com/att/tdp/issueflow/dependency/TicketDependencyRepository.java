package com.att.tdp.issueflow.dependency;

import com.att.tdp.issueflow.common.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketDependencyRepository extends JpaRepository<TicketDependency, Long> {

    boolean existsByTicketIdAndBlockedById(Long ticketId, Long blockedById);

    Optional<TicketDependency> findByTicketIdAndBlockedById(Long ticketId, Long blockedById);

    /** Blockers of a ticket, excluding soft-deleted blocker tickets. */
    List<TicketDependency> findByTicketIdAndBlockedByDeletedFalse(Long ticketId);

    /** True if the ticket has any active blocker whose status is not the resolved one. */
    @Query("""
            select count(d) > 0 from TicketDependency d
            where d.ticket.id = :ticketId
              and d.blockedBy.deleted = false
              and d.blockedBy.status <> :resolvedStatus
            """)
    boolean hasUnresolvedBlockers(@Param("ticketId") Long ticketId,
                                  @Param("resolvedStatus") Status resolvedStatus);
}
