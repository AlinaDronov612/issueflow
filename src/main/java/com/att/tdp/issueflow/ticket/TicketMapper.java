package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.ticket.dto.TicketResponse;
import org.springframework.stereotype.Component;

/** Maps {@link Ticket} entities to response DTOs so entities never leak to the API. */
@Component
public class TicketMapper {

    public TicketResponse toResponse(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getType(),
                ticket.getProject().getId(),
                ticket.getAssignee() != null ? ticket.getAssignee().getId() : null,
                ticket.getDueDate(),
                ticket.isOverdue()
        );
    }
}
