package com.gjl.music.auth.dto;

/**
 * 更新个人资料请求（自服务）。
 */
public record UpdateProfileRequest(
        String displayName,
        String email
) {}
