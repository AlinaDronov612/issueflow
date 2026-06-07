package com.att.tdp.issueflow.ticket.dto;

import com.att.tdp.issueflow.common.enums.Priority;
import com.att.tdp.issueflow.common.enums.Status;

import java.time.Instant;

/**
 * Request body for {@code PATCH /tickets/{ticketId}}. All fields optional
 * (partial update); {@code type} is intentionally absent (immutable after create).
 */
public record UpdateTicketRequest(
        String title,
        String description,
        Status status,
        Priority priority,
        Long assigneeId,
        Instant dueDate
) {
}
