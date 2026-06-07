package com.att.tdp.issueflow.ticket.dto;

import com.att.tdp.issueflow.common.enums.Priority;
import com.att.tdp.issueflow.common.enums.Status;
import com.att.tdp.issueflow.common.enums.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Request body for {@code POST /tickets}. {@code status} is optional (defaults to
 * TODO); {@code assigneeId} and {@code dueDate} are optional.
 *
 * <p>{@code @Size} caps mirror the entity column lengths so over-length values are
 * rejected as a clean 400 (and, for CSV import, a per-row error) rather than
 * failing at DB flush time.
 */
public record CreateTicketRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 4000) String description,
        Status status,
        @NotNull Priority priority,
        @NotNull TicketType type,
        @NotNull Long projectId,
        Long assigneeId,
        Instant dueDate
) {
}
