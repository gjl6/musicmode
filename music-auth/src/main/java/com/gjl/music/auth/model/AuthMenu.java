package com.gjl.music.auth.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 菜单/路由实体 — 对应 auth_menu 表。
 *
 * <p>存储前端 Vue Router 路由的元数据（路径、组件键、权限要求），
 * 后端按用户权限过滤后返回菜单树，前端动态注册路由。
 */
@Data
@NoArgsConstructor
public class AuthMenu {

    private Long id;
    private Long parentId;
    private String routeName;
    private String routePath;
    private String componentKey;
    private String permissionCode;
    private Boolean requiresAdmin;
    private Integer sortOrder;
    private Boolean isVisible;
    private String icon;
    private LocalDateTime createTime;
}
