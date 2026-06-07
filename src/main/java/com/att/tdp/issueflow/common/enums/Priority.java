package com.att.tdp.issueflow.common.enums;

/**
 * Ticket priority. Declaration order is the escalation order:
 * LOW -> MEDIUM -> HIGH -> CRITICAL. Auto-escalation promotes one level and
 * never advances past CRITICAL.
 */
public enum Priority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
