package com.gjl.music.auth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthAuditLog {

    private Long id;

    private Long operatorId;

    private String action;

    private String targetType;

    private String targetId;

    private String detail;

    private String ipAddress;

    private LocalDateTime createTime;


    public static final String ACTION_USER_CREATE = "USER_CREATE";
    public static final String ACTION_USER_UPDATE = "USER_UPDATE";
    public static final String ACTION_USER_DELETE = "USER_DELETE";
    public static final String ACTION_USER_PASSWORD = "USER_PASSWORD";
    public static final String ACTION_USER_ROLES = "USER_ROLES";
    public static final String ACTION_ROLE_CREATE = "ROLE_CREATE";
    public static final String ACTION_ROLE_UPDATE = "ROLE_UPDATE";
    public static final String ACTION_ROLE_DELETE = "ROLE_DELETE";
    public static final String ACTION_ROLE_PERMS = "ROLE_PERMS";
}
