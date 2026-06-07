package com.att.tdp.issueflow.common.exception;

/**
 * Thrown for state conflicts: illegal status transitions, updates to a DONE
 * ticket, concurrent-edit (optimistic lock) failures, deleting a referenced
 * user, etc. Maps to 409 Conflict.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
