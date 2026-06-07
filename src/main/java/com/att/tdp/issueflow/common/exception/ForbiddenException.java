package com.att.tdp.issueflow.common.exception;

/** Thrown when an authenticated user lacks the required role. Maps to 403 Forbidden. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
