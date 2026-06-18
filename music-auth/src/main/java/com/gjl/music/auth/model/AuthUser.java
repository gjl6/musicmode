package com.gjl.music.auth.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Data
@NoArgsConstructor
public class AuthUser {

    private Long id;


    private String username;


    private String password;


    private String email;


    private String displayName;


    private Integer status;


    private LocalDateTime lastLoginTime;


    private Integer failedAttempts;


    private LocalDateTime lockedUntil;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
