package com.gjl.music.auth.controller;

import com.gjl.music.auth.dto.*;
import com.gjl.music.auth.mapper.AuthUserMapper;
import com.gjl.music.auth.model.AuthUser;
import com.gjl.music.auth.security.JwtTokenProvider;
import com.gjl.music.auth.service.UserAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Slf4j
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasAuthority('user:manage')")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserAdminService userAdminService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthUserMapper authUserMapper;


    @GetMapping
    public ResponseEntity<?> list(@RequestParam(defaultValue = "") String keyword,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int size,
                                  @RequestParam(defaultValue = "id") String orderBy,
                                  @RequestParam(defaultValue = "ASC") String orderDir) {
        PageResult<UserListResponse> result = userAdminService.searchUsers(keyword, page, size, orderBy, orderDir);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        UserListResponse user = userAdminService.getUser(id);
        if (user == null) {
            return ResponseEntity.status(404).body(errorBody(404, "用户不存在"));
        }
        return ResponseEntity.ok(user);
    }


    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateUserRequest req,
                                    @RequestHeader("Authorization") String authHeader) {
        Long operatorId = resolveUserId(authHeader);
        try {
            UserListResponse user = userAdminService.createUser(req, operatorId);
            return ResponseEntity.ok(user);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorBody(400, e.getMessage()));
        }
    }


    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @Valid @RequestBody UpdateUserRequest req,
                                    @RequestHeader("Authorization") String authHeader) {
        Long operatorId = resolveUserId(authHeader);
        try {
            UserListResponse user = userAdminService.updateUser(id, req, operatorId);
            return ResponseEntity.ok(user);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorBody(400, e.getMessage()));
        }
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id,
                                    @RequestHeader("Authorization") String authHeader) {
        Long operatorId = resolveUserId(authHeader);
        try {
            userAdminService.deleteUser(id, operatorId);
            return ResponseEntity.ok(errorBody(200, "用户已删除"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorBody(400, e.getMessage()));
        }
    }


    @PutMapping("/{id}/password")
    public ResponseEntity<?> resetPassword(@PathVariable Long id,
                                           @Valid @RequestBody ChangePasswordRequest req,
                                           @RequestHeader("Authorization") String authHeader) {
        Long operatorId = resolveUserId(authHeader);
        try {
            userAdminService.resetPassword(id, req.getNewPassword(), operatorId);
            return ResponseEntity.ok(errorBody(200, "密码已重置，用户需重新登录"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorBody(400, e.getMessage()));
        }
    }


    @PutMapping("/{id}/roles")
    public ResponseEntity<?> assignRoles(@PathVariable Long id,
                                         @RequestBody List<Long> roleIds,
                                         @RequestHeader("Authorization") String authHeader) {
        Long operatorId = resolveUserId(authHeader);
        try {
            userAdminService.assignRoles(id, roleIds, operatorId);
            return ResponseEntity.ok(errorBody(200, "角色分配成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorBody(400, e.getMessage()));
        }
    }


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
