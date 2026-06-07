package com.att.tdp.issueflow.common.exception;

/** Thrown for invalid input or business-rule violations. Maps to 400 Bad Request. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
