package com.gjl.music.auth.service;

import com.gjl.music.auth.mapper.AuthUserMapper;
import com.gjl.music.auth.model.AuthUser;
import com.gjl.music.playback.infra.subsonic.SubsonicUserBridge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * SubsonicUserBridge 实现 — 委托 auth 模块的 Mapper/Service。
 *
 * <p>位于 music-auth，在运行时由 Spring 注入 music-playback 的 SubsonicServiceImpl。
 */
@Slf4j
@Component
public class SubsonicUserBridgeImpl implements SubsonicUserBridge {

    private final AuthUserMapper userMapper;
    private final UserAdminService userAdminService;

    public SubsonicUserBridgeImpl(AuthUserMapper userMapper,
                                  UserAdminService userAdminService) {
        this.userMapper = userMapper;
        this.userAdminService = userAdminService;
    }

    @Override
    public List<Map<String, Object>> getAllUsers() {
        List<AuthUser> users = userMapper.selectAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (AuthUser u : users) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", u.getUsername());
            m.put("email", u.getEmail() != null ? u.getEmail() : "");
            m.put("scrobblingEnabled", true);
            m.put("adminRole", false);  // 简化实现，不做角色反查
            m.put("settingsRole", true);
            m.put("downloadRole", true);
            m.put("uploadRole", false);
            m.put("playlistRole", true);
            m.put("coverArtRole", true);
            m.put("commentRole", false);
            m.put("podcastRole", false);
            m.put("streamRole", true);
            m.put("jukeboxRole", false);
            m.put("shareRole", false);
            m.put("videoConversionRole", false);
            m.put("folder", List.of(1));
            result.add(m);
        }
        return result;
    }

    @Override
    public void changePassword(String username, String newPassword) {
        // 查找用户 ID，委托 UserAdminService.resetPassword（管理员操作）
        AuthUser user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在: " + username);
        }
        // 管理员操作，operatorId 设为 0（系统）
        userAdminService.resetPassword(user.getId(), newPassword, 0L);
        log.info("Subsonic changePassword: username={}", username);
    }
}
