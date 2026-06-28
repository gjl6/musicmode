package com.gjl.music.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Token 响应。
 */
@Data
@Builder
public class TokenResponse {

    private String accessToken;
    private String refreshToken;
    /** Token 类型固定为 "Bearer" */
    private String tokenType;
    /** Access Token 过期时间（秒） */
    private long expiresIn;
    /** 用户基本信息 */
    private UserInfo userInfo;
    /** 用户可见的菜单/路由树（按权限过滤） */
    private List<MenuNode> menus;

    @Data
    @Builder
    public static class UserInfo {
        private Long id;
        private String username;
        private String displayName;
        private String email;
        private String avatarPath;
        private List<String> roles;
    }
}
