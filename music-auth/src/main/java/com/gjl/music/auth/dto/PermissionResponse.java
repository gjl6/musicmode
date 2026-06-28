package com.gjl.music.auth.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 权限响应。
 */
@Data
@Builder
public class PermissionResponse {

    private Long id;
    private String permissionName;
    private String permissionCode;
    private String description;
    /** 拥有此权限的角色数 */
    private long roleCount;
}
