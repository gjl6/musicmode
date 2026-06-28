package com.gjl.music.auth.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 角色实体。
 */
@Data
@NoArgsConstructor
public class AuthRole {

    private Long id;

    /** 角色名（显示） */
    private String roleName;

    /** 角色代码（Spring Security 用）：ROLE_ADMIN, ROLE_USER */
    private String roleCode;

    /** 描述 */
    private String description;

    private LocalDateTime createTime;
}
