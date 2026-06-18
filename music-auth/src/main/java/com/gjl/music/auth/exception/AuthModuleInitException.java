package com.gjl.music.auth.exception;


public class AuthModuleInitException extends RuntimeException {

    public AuthModuleInitException(String message) {
        super(message);
    }

    public AuthModuleInitException(String message, Throwable cause) {
        super(message, cause);
    }
}
