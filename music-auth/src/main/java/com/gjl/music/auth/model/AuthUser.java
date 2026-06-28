package com.gjl.music.auth.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 认证用户实体（使用自增 Long ID，不继承 MetadataItem）。
 */
@Data
@NoArgsConstructor
public class AuthUser {

    private Long id;

    /** 用户名（唯一） */
    private String username;

    /** BCrypt 加密后的密码 */
    private String password;

    /** AES 加密的明文密码副本（用于 Subsonic token+salt 认证，可为 null 表示不支持） */
    private String subsonicSecret;

    /** 邮箱 */
    private String email;

    /** 显示名称 */
    private String displayName;

    /** 头像文件路径 */
    private String avatarPath;

    /** 状态：1=正常, 0=禁用 */
    private Integer status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    /** 登录失败次数（Phase C 启用） */
    private Integer failedAttempts;

    /** 锁定到何时（Phase C 启用） */
    private LocalDateTime lockedUntil;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
