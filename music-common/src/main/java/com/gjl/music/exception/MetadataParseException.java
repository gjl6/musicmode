package com.gjl.music.exception;


public class MetadataParseException extends RuntimeException {
    private String errorCode;

    public MetadataParseException(String message) {
        super(message);
    }

    public MetadataParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public MetadataParseException withErrorCode(String code) {
        this.errorCode = code;
        return this;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
