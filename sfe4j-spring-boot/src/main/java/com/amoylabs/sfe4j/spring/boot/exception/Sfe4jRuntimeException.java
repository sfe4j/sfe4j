package com.amoylabs.sfe4j.spring.boot.exception;

public class Sfe4jRuntimeException extends RuntimeException {

    private static final long serialVersionUID = 3277837318015113281L;

    public Sfe4jRuntimeException(String message) {
        super(message);
    }

    public Sfe4jRuntimeException(Throwable cause) {
        super(cause);
    }

    public Sfe4jRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

}
