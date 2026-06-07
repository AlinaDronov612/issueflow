package com.att.tdp.issueflow.ticket.dto;

import java.util.List;

/**
 * Per-import result for {@code POST /tickets/import}: how many rows were created,
 * how many failed validation, and a per-row error message for each failure.
 */
public record TicketImportSummary(
        int created,
        int failed,
        List<String> errors
) {
}
