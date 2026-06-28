package com.gjl.music.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色响应。
 */
@Data
@Builder
public class RoleResponse {

    private Long id;
    private String roleName;
    private String roleCode;
    private String description;
    private LocalDateTime createTime;
    /** 拥有的权限码列表 */
    private List<String> permissions;
    /** 权限数量（用于列表缩略显示） */
    private int permissionCount;
    /** 此角色下的用户数 */
    private long userCount;
}
