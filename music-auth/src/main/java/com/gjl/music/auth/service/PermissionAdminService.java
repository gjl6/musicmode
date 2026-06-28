package com.gjl.music.auth.service;

import com.gjl.music.auth.dto.PermissionResponse;
import com.gjl.music.auth.mapper.AuthPermissionMapper;
import com.gjl.music.auth.mapper.AuthRoleMapper;
import com.gjl.music.auth.model.AuthPermission;
import com.gjl.music.auth.security.AuthCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 权限管理服务（只读，Redis 缓存 → DB）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionAdminService {

    private final AuthPermissionMapper permissionMapper;
    private final AuthRoleMapper roleMapper;
    private final AuthCacheService cache;

    /**
     * 获取所有权限列表（Redis 缓存 → DB）。
     */
    public List<PermissionResponse> getPermissions() {
        // 先查 Redis 缓存
        var cached = cache.getAllPermissions();
        if (cached != null) {
            return cached.stream()
                    .map(m -> PermissionResponse.builder()
                            .id(m.id())
                            .permissionName(m.permissionName())
                            .permissionCode(m.permissionCode())
                            .description(m.description())
                            .roleCount(0)
                            .build())
                    .collect(Collectors.toList());
        }

        // DB 查询
        List<AuthPermission> perms = permissionMapper.selectAll();
        List<PermissionResponse> result = perms.stream()
                .map(p -> PermissionResponse.builder()
                        .id(p.getId())
                        .permissionName(p.getPermissionName())
                        .permissionCode(p.getPermissionCode())
                        .description(p.getDescription())
                        .roleCount(0)
                        .build())
                .collect(Collectors.toList());

        // 回填缓存
        var metas = perms.stream()
                .map(p -> new AuthCacheService.PermissionMeta(
                        p.getId(), p.getPermissionCode(), p.getPermissionName(), p.getDescription()))
                .toList();
        cache.putAllPermissions(metas);

        return result;
    }

    /**
     * 获取用户所有权限码。
     */
    public List<String> getUserPermissionCodes(Long userId) {
        // Redis → DB
        var cached = cache.getPermissions(userId);
        if (cached != null) {
            return cached;
        }

        List<AuthPermission> perms = permissionMapper.selectByUserId(userId);
        List<String> codes = perms.stream()
                .map(AuthPermission::getPermissionCode)
                .collect(Collectors.toList());

        cache.putPermissions(userId, codes);
        return codes;
    }

    /**
     * 获取用户所有权限详情（含 code + name）。
     */
    public List<PermissionResponse> getUserPermissionInfos(Long userId) {
        List<AuthPermission> perms = permissionMapper.selectByUserId(userId);
        return perms.stream()
                .map(p -> PermissionResponse.builder()
                        .id(p.getId())
                        .permissionName(p.getPermissionName())
                        .permissionCode(p.getPermissionCode())
                        .description(p.getDescription())
                        .roleCount(0)
                        .build())
                .collect(Collectors.toList());
    }
}
