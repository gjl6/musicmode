package com.gjl.music.exception;


public class ModuleException extends RuntimeException {

    private final String moduleName;
    private final String itemKey;
    private String errorCode;

    public ModuleException(String moduleName, String itemKey, String message, Throwable cause) {
        super(message, cause);
        this.moduleName = moduleName;
        this.itemKey = itemKey;
    }

    public ModuleException(String moduleName, String message, Throwable cause) {
        this(moduleName, null, message, cause);
    }

    public ModuleException(String moduleName, String message) {
        this(moduleName, null, message, null);
    }

    public ModuleException withErrorCode(String errorCode) {
        this.errorCode = errorCode;
        return this;
    }

    public String getModuleName() { return moduleName; }
    public String getItemKey() { return itemKey; }
    public String getErrorCode() { return errorCode; }
}
