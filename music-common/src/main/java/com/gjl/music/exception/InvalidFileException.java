package com.gjl.music.exception;

/** 文件格式或内容非法异常 */
public class InvalidFileException extends MetadataParseException {
    public InvalidFileException(String message) {
        super(message);
    }

    public InvalidFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
