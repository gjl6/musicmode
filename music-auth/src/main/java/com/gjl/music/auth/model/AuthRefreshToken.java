package com.gjl.music.auth.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Refresh Token 实体。
 */
@Data
@NoArgsConstructor
public class AuthRefreshToken {

    /** UUID 主键 */
    private String id;

    /** 所属用户 ID */
    private Long userId;

    /** Token 值（SHA-256 哈希存储） */
    private String token;

    /** 过期时间 */
    private LocalDateTime expiresAt;

    /** 是否已吊销 */
    private Boolean revoked;

    /** 创建时间 */
    private LocalDateTime createTime;

    public AuthRefreshToken(Long userId, String token, LocalDateTime expiresAt) {
        this.id = java.util.UUID.randomUUID().toString();
        this.userId = userId;
        this.token = token;
        this.expiresAt = expiresAt;
        this.revoked = false;
        this.createTime = LocalDateTime.now();
    }
}
