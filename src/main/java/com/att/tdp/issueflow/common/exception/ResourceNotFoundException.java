package com.att.tdp.issueflow.common.exception;

/** Thrown when a requested entity does not exist. Maps to 404 Not Found. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String entity, Object id) {
        super(entity + " with id " + id + " not found");
    }
}
