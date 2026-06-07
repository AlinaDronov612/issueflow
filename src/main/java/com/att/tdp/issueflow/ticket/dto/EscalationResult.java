package com.att.tdp.issueflow.ticket.dto;

/** Result of an on-demand escalation sweep: how many tickets were escalated. */
public record EscalationResult(int escalated) {
}
