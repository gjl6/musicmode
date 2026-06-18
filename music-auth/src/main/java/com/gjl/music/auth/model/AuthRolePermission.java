package com.gjl.music.auth.model;

import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
public class AuthRolePermission {

    private Long roleId;
    private Long permissionId;
}
