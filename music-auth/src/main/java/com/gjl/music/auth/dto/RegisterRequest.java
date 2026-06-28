package com.gjl.music.auth.dto;

/**
 * 注册请求。
 */
public record RegisterRequest(String username, String password, String email, String displayName) {
}
