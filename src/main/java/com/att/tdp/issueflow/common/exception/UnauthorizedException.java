package com.att.tdp.issueflow.common.exception;

/** Thrown on authentication failure (bad credentials, missing/invalid token). Maps to 401 Unauthorized. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
