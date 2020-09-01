package com.mservicetech.openapi.common;

public class OpenApiLoadException extends RuntimeException{
    /**
     * Create an OpenApiLoadException with a specific message.
     * @param message the message
     */
    public OpenApiLoadException(String message) {
        super(message);
    }

    /**
     * Create an OpenApiLoadException with a specific message and cause.
     * @param message the message
     * @param cause the cause
     */
    public OpenApiLoadException(String message, Exception cause) {
        super(message, cause);
    }
}
