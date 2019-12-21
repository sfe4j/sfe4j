package com.v2code.sfe4j.exception;

import org.apache.commons.lang3.exception.ContextedRuntimeException;

public class Sfe4jRuntimeException extends ContextedRuntimeException {

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
