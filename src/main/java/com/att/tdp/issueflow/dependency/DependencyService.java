package com.att.tdp.issueflow.dependency;

import com.att.tdp.issueflow.audit.AuditService;
import com.att.tdp.issueflow.common.enums.AuditAction;
import com.att.tdp.issueflow.common.enums.AuditEntityType;
import com.att.tdp.issueflow.common.exception.BadRequestException;
import com.att.tdp.issueflow.common.exception.ConflictException;
import com.att.tdp.issueflow.common.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.dependency.dto.DependencyResponse;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DependencyService {

    private final TicketDependencyRepository dependencyRepository;
    private final TicketRepository ticketRepository;
    private final AuditService auditService;

    @Transactional
    public void addDependency(Long ticketId, Long blockerId) {
        if (blockerId.equals(ticketId)) {
            throw new BadRequestException("a ticket cannot depend on itself");
        }
        Ticket blocked = activeTicketOrThrow(ticketId);
        Ticket blocker = activeTicketOrThrow(blockerId);

        if (!blocked.getProject().getId().equals(blocker.getProject().getId())) {
            throw new BadRequestException("a dependency must be between tickets in the same project");
        }
        if (dependencyRepository.existsByTicketIdAndBlockedById(ticketId, blockerId)) {
            throw new ConflictException(
                    "ticket " + ticketId + " is already blocked by ticket " + blockerId);
        }

        TicketDependency dependency = dependencyRepository.save(
                TicketDependency.builder().ticket(blocked).blockedBy(blocker).build());
        auditService.record(AuditAction.CREATE, AuditEntityType.DEPENDENCY, dependency.getId());
    }

    @Transactional(readOnly = true)
    public List<DependencyResponse> getDependencies(Long ticketId) {
        activeTicketOrThrow(ticketId);
        return dependencyRepository.findByTicketIdAndBlockedByDeletedFalse(ticketId).stream()
                .map(d -> new DependencyResponse(
                        d.getBlockedBy().getId(),
                        d.getBlockedBy().getTitle(),
                        d.getBlockedBy().getStatus()))
                .toList();
    }

    @Transactional
    public void removeDependency(Long ticketId, Long blockerId) {
        activeTicketOrThrow(ticketId);
        TicketDependency dependency = dependencyRepository
                .findByTicketIdAndBlockedById(ticketId, blockerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dependency of ticket " + ticketId + " blocked by " + blockerId + " not found"));
        Long dependencyId = dependency.getId();
        dependencyRepository.delete(dependency);
        auditService.record(AuditAction.DELETE, AuditEntityType.DEPENDENCY, dependencyId);
    }

    private Ticket activeTicketOrThrow(Long ticketId) {
        return ticketRepository.findByIdAndDeletedFalse(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
    }
}
