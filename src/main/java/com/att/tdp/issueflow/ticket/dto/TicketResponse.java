package com.att.tdp.issueflow.ticket.dto;

import com.att.tdp.issueflow.common.enums.Priority;
import com.att.tdp.issueflow.common.enums.Status;
import com.att.tdp.issueflow.common.enums.TicketType;

import java.time.Instant;

/** Response body for ticket endpoints. {@code assigneeId} may be null (unassigned). */
public record TicketResponse(
        Long id,
        String title,
        String description,
        Status status,
        Priority priority,
        TicketType type,
        Long projectId,
        Long assigneeId,
        Instant dueDate,
        boolean isOverdue
) {
}
