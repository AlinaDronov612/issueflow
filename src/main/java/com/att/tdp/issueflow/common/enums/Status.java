package com.att.tdp.issueflow.common.enums;

/**
 * Ticket lifecycle. Declaration order is the only allowed forward direction:
 * TODO -> IN_PROGRESS -> IN_REVIEW -> DONE. Backward transitions are rejected.
 */
public enum Status {
    TODO,
    IN_PROGRESS,
    IN_REVIEW,
    DONE
}
