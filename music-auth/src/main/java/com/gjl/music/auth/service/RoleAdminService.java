package com.gjl.music.auth.service;

import com.gjl.music.auth.dto.AssignPermissionsRequest;
import com.gjl.music.auth.dto.CreateRoleRequest;
import com.gjl.music.auth.dto.RoleResponse;
import com.gjl.music.auth.dto.UpdateRoleRequest;
import com.gjl.music.auth.mapper.AuthAuditLogMapper;
import com.gjl.music.auth.mapper.AuthPermissionMapper;
import com.gjl.music.auth.mapper.AuthRefreshTokenMapper;
import com.gjl.music.auth.mapper.AuthRoleMapper;
import com.gjl.music.auth.mapper.AuthUserMapper;
import com.gjl.music.auth.model.AuthAuditLog;
import com.gjl.music.auth.model.AuthPermission;
import com.gjl.music.auth.model.AuthRole;
import com.gjl.music.auth.model.AuthUser;
import com.gjl.music.auth.security.AuthCacheService;
import com.gjl.music.auth.security.PermissionConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色管理服务。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoleAdminService {

    private final AuthRoleMapper roleMapper;
    private final AuthPermissionMapper permissionMapper;
    private final AuthUserMapper userMapper;
    private final AuthRefreshTokenMapper refreshTokenMapper;
    private final AuthAuditLogMapper auditLogMapper;
    private final AuthCacheService cache;

    /**
     * 获取所有角色（含权限列表和用户数）。
     */
    public List<RoleResponse> getRoles() {
        List<AuthRole> roles = roleMapper.selectAll();
        return roles.stream()
                .map(r -> {
                    List<AuthPermission> perms = permissionMapper.selectByRoleId(r.getId());
                    List<AuthUser> users = userMapper.selectByRoleCode(r.getRoleCode());
                    return RoleResponse.builder()
                            .id(r.getId())
                            .roleName(r.getRoleName())
                            .roleCode(r.getRoleCode())
                            .description(r.getDescription())
                            .createTime(r.getCreateTime())
                            .permissions(perms.stream().map(AuthPermission::getPermissionCode).collect(Collectors.toList()))
                            .permissionCount(perms.size())
                            .userCount(users.size())
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取单个角色。
     */
    public RoleResponse getRole(Long id) {
        AuthRole role = roleMapper.selectById(id);
        if (role == null) {
            return null;
        }
        List<AuthPermission> perms = permissionMapper.selectByRoleId(id);
        List<AuthUser> users = userMapper.selectByRoleCode(role.getRoleCode());
        return RoleResponse.builder()
                .id(role.getId())
                .roleName(role.getRoleName())
                .roleCode(role.getRoleCode())
                .description(role.getDescription())
                .createTime(role.getCreateTime())
                .permissions(perms.stream().map(AuthPermission::getPermissionCode).collect(Collectors.toList()))
                .permissionCount(perms.size())
                .userCount(users.size())
                .build();
    }

    /**
     * 创建角色。
     */
    @Transactional
    public RoleResponse createRole(CreateRoleRequest req, Long operatorId) {
        AuthRole role = new AuthRole();
        role.setRoleName(req.getRoleName());
        role.setRoleCode(req.getRoleCode());
        role.setDescription(req.getDescription());
        roleMapper.insert(role);

        audit(operatorId, AuthAuditLog.ACTION_ROLE_CREATE, "ROLE", req.getRoleCode(),
                "name=" + req.getRoleName());

        log.info("角色创建: {} by operator={}", req.getRoleCode(), operatorId);
        return RoleResponse.builder()
                .id(role.getId())
                .roleName(role.getRoleName())
                .roleCode(role.getRoleCode())
                .description(role.getDescription())
                .createTime(role.getCreateTime())
                .permissions(List.of())
                .permissionCount(0)
                .userCount(0)
                .build();
    }

    /**
     * 更新角色（不能修改 roleCode）。
     */
    @Transactional
    public RoleResponse updateRole(Long id, UpdateRoleRequest req, Long operatorId) {
        AuthRole role = roleMapper.selectById(id);
        if (role == null) {
            return null;
        }
        if (req.getRoleName() != null) {
            role.setRoleName(req.getRoleName());
        }
        if (req.getDescription() != null) {
            role.setDescription(req.getDescription());
        }
        roleMapper.updateById(role);

        audit(operatorId, AuthAuditLog.ACTION_ROLE_UPDATE, "ROLE", role.getRoleCode(),
                "name=" + role.getRoleName());

        log.info("角色更新: {} by operator={}", role.getRoleCode(), operatorId);
        return getRole(id);
    }

    /**
     * 删除角色（内置角色禁止删除，有用户使用的角色禁止删除）。
     */
    @Transactional
    public void deleteRole(Long id, Long operatorId) {
        AuthRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在: id=" + id);
        }

        // 内置角色保护
        if (PermissionConstants.PROTECTED_ROLES.contains(role.getRoleCode())) {
            throw new IllegalArgumentException("内置角色 " + role.getRoleCode() + " 不可删除");
        }

        // 检查是否有用户正在使用此角色
        List<AuthUser> users = userMapper.selectByRoleCode(role.getRoleCode());
        if (!users.isEmpty()) {
            throw new IllegalArgumentException(
                    "角色 " + role.getRoleCode() + " 下还有 " + users.size() + " 个用户，请先移除用户再删除角色");
        }

        roleMapper.deleteById(id);

        audit(operatorId, AuthAuditLog.ACTION_ROLE_DELETE, "ROLE", role.getRoleCode(),
                "name=" + role.getRoleName());

        log.info("角色删除: {} by operator={}", role.getRoleCode(), operatorId);
    }

    /**
     * 为角色分配权限。
     */
    @Transactional
    public void assignPermissions(Long roleId, List<Long> permIds, Long operatorId) {
        AuthRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在: id=" + roleId);
        }

        // 内置角色保护：不允许移除 user:write 权限
        if (PermissionConstants.PROTECTED_ROLES.contains(role.getRoleCode())) {
            AuthPermission userWritePerm = permissionMapper.selectByCode(PermissionConstants.USER_WRITE);
            if (userWritePerm != null && !permIds.contains(userWritePerm.getId())) {
                throw new IllegalArgumentException(
                        "不能从 " + role.getRoleCode() + " 角色移除 " + PermissionConstants.USER_WRITE + " 权限");
            }
        }

        // 清空旧权限
        permissionMapper.deleteRolePermissions(roleId);
        // 分配新权限
        for (Long permId : permIds) {
            permissionMapper.insertRolePermission(roleId, permId);
        }

        // 权限变更 → 递增 token 版本 + 吊销 refreshToken + 失效缓存 → 强制重新登录
        List<AuthUser> affectedUsers = userMapper.selectByRoleCode(role.getRoleCode());
        for (AuthUser user : affectedUsers) {
            cache.incrementVersion(user.getUsername());
            refreshTokenMapper.revokeByUserId(user.getId());
            cache.evictPermissions(user.getId());
        }
        // 权限元数据变更 → 失效全局权限缓存
        cache.evictAllPermissions();

        audit(operatorId, AuthAuditLog.ACTION_ROLE_PERMS, "ROLE", role.getRoleCode(),
                "permIds=" + permIds);

        log.info("角色权限分配: {} permIds={}, 影响 {} 用户, by operator={}",
                role.getRoleCode(), permIds, affectedUsers.size(), operatorId);
    }

    // ── 审计日志 ──

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
