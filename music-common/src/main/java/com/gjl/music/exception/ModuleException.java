package com.gjl.music.exception;

/**
 * 模块级统一异常 —— 表示管道模块处理某项时失败。
 *
 * <p>模块直接抛出本异常即可，无需自行 catch 再 re-throw。
 * 顶层框架（PipelineImpl / GapFillingModule）统一捕获并记录到 DB。</p>
 */
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
