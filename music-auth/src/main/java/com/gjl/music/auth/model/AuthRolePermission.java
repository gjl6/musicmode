package com.gjl.music.auth.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色-权限关联实体。
 */
@Data
@NoArgsConstructor
public class AuthRolePermission {

    private Long roleId;
    private Long permissionId;
}
