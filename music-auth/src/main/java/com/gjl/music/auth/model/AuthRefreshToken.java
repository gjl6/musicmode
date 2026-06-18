package com.gjl.music.auth.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Data
@NoArgsConstructor
public class AuthRefreshToken {


    private String id;


    private Long userId;


    private String token;


    private LocalDateTime expiresAt;


    private Boolean revoked;


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
