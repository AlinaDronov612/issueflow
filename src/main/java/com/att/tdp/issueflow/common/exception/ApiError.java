package com.att.tdp.issueflow.common.exception;

import java.time.Instant;

/**
 * Consistent error payload returned by {@link GlobalExceptionHandler}, e.g.
 * { "timestamp": "...", "status": 400, "error": "Bad Request",
 *   "message": "role must be one of ADMIN, DEVELOPER", "path": "/users" }
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
