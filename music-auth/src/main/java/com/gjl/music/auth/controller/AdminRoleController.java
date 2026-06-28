package com.gjl.music.auth.controller;

import com.gjl.music.auth.dto.*;
import com.gjl.music.auth.mapper.AuthUserMapper;
import com.gjl.music.auth.model.AuthUser;
import com.gjl.music.auth.security.JwtTokenProvider;
import com.gjl.music.auth.service.RoleAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色管理 REST API（管理员）。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/roles")
@PreAuthorize("hasAuthority('user:write')")
@RequiredArgsConstructor
public class AdminRoleController {

    private final RoleAdminService roleAdminService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthUserMapper authUserMapper;

    // ── 查询 ──

    @GetMapping
    public ResponseEntity<?> list() {
        List<RoleResponse> roles = roleAdminService.getRoles();
        return ResponseEntity.ok(roles);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        RoleResponse role = roleAdminService.getRole(id);
        if (role == null) {
            return ResponseEntity.status(404).body(errorBody(404, "角色不存在"));
        }
        return ResponseEntity.ok(role);
    }

    // ── 创建 ──

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateRoleRequest req,
                                    @RequestHeader("Authorization") String authHeader) {
        Long operatorId = resolveUserId(authHeader);
        try {
            RoleResponse role = roleAdminService.createRole(req, operatorId);
            return ResponseEntity.ok(role);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorBody(400, e.getMessage()));
        }
    }

    // ── 更新 ──

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @Valid @RequestBody UpdateRoleRequest req,
                                    @RequestHeader("Authorization") String authHeader) {
        Long operatorId = resolveUserId(authHeader);
        try {
            RoleResponse role = roleAdminService.updateRole(id, req, operatorId);
            if (role == null) {
                return ResponseEntity.status(404).body(errorBody(404, "角色不存在"));
            }
            return ResponseEntity.ok(role);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorBody(400, e.getMessage()));
        }
    }

    // ── 删除 ──

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id,
                                    @RequestHeader("Authorization") String authHeader) {
        Long operatorId = resolveUserId(authHeader);
        try {
            roleAdminService.deleteRole(id, operatorId);
            return ResponseEntity.ok(errorBody(200, "角色已删除"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorBody(400, e.getMessage()));
        }
    }

    // ── 权限分配 ──

    @PutMapping("/{id}/permissions")
    public ResponseEntity<?> assignPermissions(@PathVariable Long id,
                                               @Valid @RequestBody AssignPermissionsRequest req,
                                               @RequestHeader("Authorization") String authHeader) {
        Long operatorId = resolveUserId(authHeader);
        try {
            roleAdminService.assignPermissions(id, req.getPermissionIds(), operatorId);
            return ResponseEntity.ok(errorBody(200, "权限分配成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorBody(400, e.getMessage()));
        }
    }

    // ── 辅助 ──

    private Long resolveUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            return null;
        }
        String username = jwtTokenProvider.getUsername(token);
        AuthUser user = authUserMapper.selectByUsername(username);
        return user != null ? user.getId() : null;
    }

    private static Map<String, Object> errorBody(int code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("data", null);
        return body;
    }
}
