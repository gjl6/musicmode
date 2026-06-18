package com.gjl.music.exception;


public class InvalidFileException extends MetadataParseException {
    public InvalidFileException(String message) {
        super(message);
    }

    public InvalidFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
