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


    @Transactional
    public void deleteRole(Long id, Long operatorId) {
        AuthRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在: id=" + id);
        }

                if (PermissionConstants.PROTECTED_ROLES.contains(role.getRoleCode())) {
            throw new IllegalArgumentException("内置角色 " + role.getRoleCode() + " 不可删除");
        }

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


    @Transactional
    public void assignPermissions(Long roleId, List<Long> permIds, Long operatorId) {
        AuthRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在: id=" + roleId);
        }

                if (PermissionConstants.PROTECTED_ROLES.contains(role.getRoleCode())) {
            AuthPermission userManagePerm = permissionMapper.selectByCode("user:manage");
            if (userManagePerm != null && !permIds.contains(userManagePerm.getId())) {
                throw new IllegalArgumentException(
                        "不能从 " + role.getRoleCode() + " 角色移除 user:manage 权限");
            }
        }

                permissionMapper.deleteRolePermissions(roleId);
                for (Long permId : permIds) {
            permissionMapper.insertRolePermission(roleId, permId);
        }

                List<AuthUser> affectedUsers = userMapper.selectByRoleCode(role.getRoleCode());
        for (AuthUser user : affectedUsers) {
            cache.incrementVersion(user.getUsername());
            refreshTokenMapper.revokeByUserId(user.getId());
            cache.evictPermissions(user.getId());
        }
                cache.evictAllPermissions();

        audit(operatorId, AuthAuditLog.ACTION_ROLE_PERMS, "ROLE", role.getRoleCode(),
                "permIds=" + permIds);

        log.info("角色权限分配: {} permIds={}, 影响 {} 用户, by operator={}",
                role.getRoleCode(), permIds, affectedUsers.size(), operatorId);
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
