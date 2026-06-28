package com.gjl.music.auth.dto;

/**
 * Token 刷新请求。
 */
public record RefreshRequest(String refreshToken) {
}
