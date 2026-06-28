package com.gjl.music.auth.controller;

import com.gjl.music.auth.dto.PermissionResponse;
import com.gjl.music.auth.service.PermissionAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 权限管理 REST API（管理员只读）。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/permissions")
@PreAuthorize("hasAuthority('user:write')")
@RequiredArgsConstructor
public class AdminPermissionController {

    private final PermissionAdminService permissionAdminService;

    @GetMapping
    public ResponseEntity<?> list() {
        List<PermissionResponse> perms = permissionAdminService.getPermissions();
        return ResponseEntity.ok(perms);
    }
}
