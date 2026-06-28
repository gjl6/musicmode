package com.gjl.music.auth.service;

import com.gjl.music.auth.dto.*;
import com.gjl.music.auth.mapper.AuthAuditLogMapper;
import com.gjl.music.auth.mapper.AuthRefreshTokenMapper;
import com.gjl.music.auth.mapper.AuthRoleMapper;
import com.gjl.music.auth.mapper.AuthUserMapper;
import com.gjl.music.auth.model.AuthAuditLog;
import com.gjl.music.auth.model.AuthRole;
import com.gjl.music.auth.model.AuthUser;
import com.gjl.music.auth.security.AuthCacheService;
import com.gjl.music.auth.security.PermissionConstants;
import com.gjl.music.auth.security.SubsonicSecretCrypto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户管理服务。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserAdminService {

    private final AuthUserMapper userMapper;
    private final AuthRoleMapper roleMapper;
    private final AuthRefreshTokenMapper refreshTokenMapper;
    private final AuthAuditLogMapper auditLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthCacheService cache;
    private final SubsonicSecretCrypto subsonicSecretCrypto;

    /** 允许排序的列白名单——防止 SQL 注入 */
    private static final Set<String> ALLOWED_ORDER_COLUMNS = Set.of(
            "id", "username", "email", "display_name", "status", "last_login_time", "create_time"
    );

    // ── 查询 ──

    /**
     * 分页搜索用户。
     */
    public PageResult<UserListResponse> searchUsers(String keyword, int page, int size,
                                                     String orderBy, String orderDir) {
        String safeOrderBy = sanitizeOrderBy(orderBy);
        String safeOrderDir = "DESC".equalsIgnoreCase(orderDir) ? "DESC" : "ASC";

        int offset = (page - 1) * size;
        List<AuthUser> users = userMapper.selectPage(keyword, offset, size, safeOrderBy, safeOrderDir);
        long total = userMapper.count(keyword);

        List<UserListResponse> list = users.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PageResult.of(list, total, page, size);
    }

    /**
     * 获取单个用户详情。
     */
    public UserListResponse getUser(Long id) {
        AuthUser user = userMapper.selectById(id);
        if (user == null) {
            return null;
        }
        return toResponse(user);
    }

    // ── 创建 ──

    /**
     * 创建用户。
     */
    @Transactional
    public UserListResponse createUser(CreateUserRequest req, Long operatorId) {
        if (userMapper.existsByUsername(req.getUsername())) {
            throw new IllegalArgumentException("用户名 " + req.getUsername() + " 已存在");
        }

        AuthUser user = new AuthUser();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setDisplayName(req.getDisplayName() != null ? req.getDisplayName() : req.getUsername());
        user.setEmail(req.getEmail());
        user.setStatus(1); // 默认启用

        userMapper.insert(user);

        // 分配角色
        List<Long> roleIds = req.getRoleIds();
        if (roleIds == null || roleIds.isEmpty()) {
            // 默认分配 ROLE_USER
            AuthRole defaultRole = roleMapper.selectByCode("ROLE_USER");
            if (defaultRole != null) {
                roleIds = List.of(defaultRole.getId());
            } else {
                roleIds = List.of();
            }
        }
        for (Long roleId : roleIds) {
            roleMapper.insertUserRole(user.getId(), roleId);
        }

        audit(operatorId, AuthAuditLog.ACTION_USER_CREATE, "USER", user.getUsername(),
                "displayName=" + user.getDisplayName() + ", roleIds=" + roleIds);

        log.info("用户创建: username={} by operator={}", user.getUsername(), operatorId);
        return toResponse(user);
    }

    // ── 更新 ──

    /**
     * 更新用户信息（不含密码和角色）。
     */
    @Transactional
    public UserListResponse updateUser(Long id, UpdateUserRequest req, Long operatorId) {
        AuthUser user = userMapper.selectById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在: id=" + id);
        }

        if (req.getDisplayName() != null) {
            user.setDisplayName(req.getDisplayName());
        }
        if (req.getEmail() != null) {
            user.setEmail(req.getEmail());
        }
        if (req.getStatus() != null) {
            user.setStatus(req.getStatus());
        }
        userMapper.updateById(user);

        // 如果提供了 roleIds，则替换所有角色
        if (req.getRoleIds() != null) {
            replaceRoles(user.getId(), req.getRoleIds());
            // 角色变更 → 递增 token 版本 + 吊销 refreshToken + 失效缓存 → 强制重新登录
            cache.incrementVersion(user.getUsername());
            refreshTokenMapper.revokeByUserId(user.getId());
            cache.evictUserAuth(user.getId());
        }

        // 用户信息变更 → 失效缓存
        cache.evictUser(user.getUsername());

        audit(operatorId, AuthAuditLog.ACTION_USER_UPDATE, "USER", user.getUsername(),
                "displayName=" + user.getDisplayName() + ", status=" + user.getStatus());

        log.info("用户更新: username={} by operator={}", user.getUsername(), operatorId);
        return toResponse(user);
    }

    // ── 删除 ──

    /**
     * 删除用户（内置管理员不可删除，不可删除自己）。
     */
    @Transactional
    public void deleteUser(Long id, Long operatorId) {
        AuthUser user = userMapper.selectById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在: id=" + id);
        }

        // 不能删除自己
        if (user.getId().equals(operatorId)) {
            throw new IllegalArgumentException("不能删除自己的账户");
        }

        // 检查是否是最后一个 ADMIN
        List<AuthUser> admins = userMapper.selectByRoleCode("ROLE_ADMIN");
        boolean isAdmin = admins.stream().anyMatch(a -> a.getId().equals(id));
        if (isAdmin && admins.size() <= 1) {
            throw new IllegalArgumentException("不能删除最后一个管理员，系统将无法管理");
        }

        String username = user.getUsername();
        userMapper.deleteById(id);

        // 失效缓存
        cache.evictUser(username);
        cache.evictUserAuth(id);

        audit(operatorId, AuthAuditLog.ACTION_USER_DELETE, "USER", username, null);

        log.info("用户删除: username={} by operator={}", username, operatorId);
    }

    // ── 密码 ──

    /**
     * 管理员重置用户密码。
     */
    @Transactional
    public void resetPassword(Long id, String newPassword, Long operatorId) {
        AuthUser user = userMapper.selectById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在: id=" + id);
        }

        userMapper.updatePassword(id, passwordEncoder.encode(newPassword),
                subsonicSecretCrypto.encrypt(newPassword));
        // 密码变更 → 吊销所有 refreshToken + 失效缓存 → 强制重新登录
        refreshTokenMapper.revokeByUserId(id);
        cache.evictUser(user.getUsername());

        audit(operatorId, AuthAuditLog.ACTION_USER_PASSWORD, "USER", user.getUsername(), null);

        log.info("密码重置: username={} by operator={}", user.getUsername(), operatorId);
    }

    // ── 角色分配 ──

    /**
     * 为用户分配角色。
     */
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds, Long operatorId) {
        AuthUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在: id=" + userId);
        }

        // 至少保持一个管理员
        List<AuthUser> admins = userMapper.selectByRoleCode("ROLE_ADMIN");
        boolean isCurrentlyAdmin = admins.stream().anyMatch(a -> a.getId().equals(userId));
        if (isCurrentlyAdmin) {
            AuthRole adminRole = roleMapper.selectByCode("ROLE_ADMIN");
            if (adminRole != null && !roleIds.contains(adminRole.getId()) && admins.size() <= 1) {
                throw new IllegalArgumentException("不能移除最后一个管理员的 ADMIN 角色");
            }
        }

        replaceRoles(userId, roleIds);
        // 角色变更 → 递增 token 版本 + 吊销 refreshToken + 失效缓存 → 强制重新登录
        cache.incrementVersion(user.getUsername());
        refreshTokenMapper.revokeByUserId(userId);
        cache.evictUserAuth(userId);

        audit(operatorId, AuthAuditLog.ACTION_USER_ROLES, "USER", user.getUsername(),
                "roleIds=" + roleIds);

        log.info("角色分配: username={} roleIds={} by operator={}", user.getUsername(), roleIds, operatorId);
    }

    // ── 内部方法 ──

    private void replaceRoles(Long userId, List<Long> roleIds) {
        roleMapper.deleteUserRoles(userId);
        for (Long roleId : roleIds) {
            roleMapper.insertUserRole(userId, roleId);
        }
    }

    private UserListResponse toResponse(AuthUser user) {
        List<AuthRole> roles = roleMapper.selectByUserId(user.getId());
        List<String> roleCodes = roles.stream()
                .map(AuthRole::getRoleCode)
                .collect(Collectors.toList());
        return UserListResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .email(user.getEmail())
                .status(user.getStatus())
                .lastLoginTime(user.getLastLoginTime())
                .createTime(user.getCreateTime())
                .updateTime(user.getUpdateTime())
                .roles(roleCodes)
                .build();
    }

    private String sanitizeOrderBy(String orderBy) {
        if (orderBy == null || !ALLOWED_ORDER_COLUMNS.contains(orderBy)) {
            return "id";
        }
        return orderBy;
    }

    private void audit(Long operatorId, String action, String targetType, String targetId, String detail) {
        try {
            AuthAuditLog logEntry = AuthAuditLog.builder()
                    .operatorId(operatorId)
                    .action(action)
                    .targetType(targetType)
                    .targetId(targetId)
                    .detail(detail)
                    .build();
            auditLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("审计日志写入失败: action={}, target={}", action, targetId, e);
        }
    }
}
