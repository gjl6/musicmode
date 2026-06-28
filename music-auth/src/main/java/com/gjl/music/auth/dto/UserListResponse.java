package com.gjl.music.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户列表响应。
 */
@Data
@Builder
public class UserListResponse {

    private Long id;
    private String username;
    private String displayName;
    private String email;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    /** 拥有的角色列表 */
    private List<String> roles;
}
