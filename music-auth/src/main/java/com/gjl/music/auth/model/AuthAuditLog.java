package com.gjl.music.auth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 认证审计日志 —— 记录所有管理操作。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthAuditLog {

    private Long id;
    /** 操作者用户 ID */
    private Long operatorId;
    /** 操作类型：USER_CREATE, USER_UPDATE, USER_DELETE, ROLE_CREATE, ROLE_DELETE, PERM_ASSIGN, ROLE_ASSIGN ... */
    private String action;
    /** 目标类型：USER, ROLE */
    private String targetType;
    /** 目标标识（用户名/角色码） */
    private String targetId;
    /** 操作详情（JSON） */
    private String detail;
    /** 操作者 IP */
    private String ipAddress;
    /** 操作时间 */
    private LocalDateTime createTime;

    // ── 常用操作类型常量 ──

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
