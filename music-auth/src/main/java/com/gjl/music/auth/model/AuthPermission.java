package com.gjl.music.auth.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 权限实体。
 */
@Data
@NoArgsConstructor
public class AuthPermission {

    private Long id;

    /** 权限名（显示） */
    private String permissionName;

    /** 权限代码（@PreAuthorize 用）：user:write, music:write, music:delete */
    private String permissionCode;

    /** 描述 */
    private String description;

    private LocalDateTime createTime;
}
