package com.keedio.exception;

/**
 * Created by luca on 13/2/16.
 */
public class TailerException extends RuntimeException {
    public TailerException() {
        super();
    }

    public TailerException(String message) {
        super(message);
    }

    public TailerException(String message, Throwable cause) {
        super(message, cause);
    }

    public TailerException(Throwable cause) {
        super(cause);
    }

    protected TailerException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
