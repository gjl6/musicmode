package com.gjl.music.auth;

/**
 * 认证模块初始化完成标记接口。
 *
 * <p>当 music-auth 的 SecurityConfig 成功创建此 Bean 后，
 * 其他模块（通过 {@code @DependsOn("authInitializedMarker")}）
 * 才能继续初始化。</p>
 */
public interface AuthInitializedMarker {
}
