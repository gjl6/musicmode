package com.gjl.music.auth.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户-角色关联实体。
 */
@Data
@NoArgsConstructor
public class AuthUserRole {

    private Long userId;
    private Long roleId;
}
