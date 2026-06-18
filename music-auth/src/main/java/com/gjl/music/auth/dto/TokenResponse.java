package com.gjl.music.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;


@Data
@Builder
public class TokenResponse {

    private String accessToken;
    private String refreshToken;

    private String tokenType;

    private long expiresIn;

    private UserInfo userInfo;

    @Data
    @Builder
    public static class UserInfo {
        private Long id;
        private String username;
        private String displayName;
        private String email;
        private List<String> roles;
    }
}
