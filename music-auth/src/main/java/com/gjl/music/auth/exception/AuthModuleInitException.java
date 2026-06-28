package com.gjl.music.auth.exception;

/**
 * 认证模块致命启动异常。
 *
 * <p>当 JWT secret 未配置等致命错误发生时抛出，
 * 导致 authInitializedMarker Bean 创建失败，
 * 进而 Spring context refresh 失败，应用退出。</p>
 */
public class AuthModuleInitException extends RuntimeException {

    public AuthModuleInitException(String message) {
        super(message);
    }

    public AuthModuleInitException(String message, Throwable cause) {
        super(message, cause);
    }
}
