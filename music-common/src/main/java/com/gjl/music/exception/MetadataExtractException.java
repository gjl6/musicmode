package com.gjl.music.exception;

/** 元数据提取过程中发生的异常 */
public class MetadataExtractException extends MetadataParseException {

    public enum ExtractErrorType {
        TAG_READ_ERROR,
        INVALID_FRAME_FORMAT,
        ARTWORK_PARSING_FAILED,
        LYRICS_EXTRACTION_ERROR,
        TECHNICAL_DATA_INVALID,
        UNSUPPORTED_TAG_VERSION
    }

    private final ExtractErrorType errorType;
    private final String fieldName;
    private final String audioFormat;

    public MetadataExtractException(String message, ExtractErrorType errorType) {
        super(message);
        this.errorType = errorType;
        this.fieldName = null;
        this.audioFormat = null;
    }

    public MetadataExtractException(String message, ExtractErrorType errorType, String fieldName) {
        super(message);
        this.errorType = errorType;
        this.fieldName = fieldName;
        this.audioFormat = null;
    }

    public MetadataExtractException(String message, ExtractErrorType errorType, String fieldName, String audioFormat) {
        super(message);
        this.errorType = errorType;
        this.fieldName = fieldName;
        this.audioFormat = audioFormat;
    }

    public ExtractErrorType getErrorType() {
        return errorType;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getAudioFormat() {
        return audioFormat;
    }
}
