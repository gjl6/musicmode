package com.gjl.music.auth.security;

import com.gjl.music.auth.mapper.AuthPermissionMapper;
import com.gjl.music.auth.mapper.AuthRoleMapper;
import com.gjl.music.auth.mapper.AuthUserMapper;
import com.gjl.music.auth.model.AuthPermission;
import com.gjl.music.auth.model.AuthRole;
import com.gjl.music.auth.model.AuthUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 从 DB 加载用户详情，Redis 缓存加速。
 * <p>
 * 缓存策略：先查 Redis → 未命中则查 DB → 写入 Redis。
 * Redis 不可用时降级为直查 DB。
 */
@Slf4j
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final AuthUserMapper authUserMapper;
    private final AuthRoleMapper authRoleMapper;
    private final AuthPermissionMapper authPermissionMapper;
    private final AuthCacheService cache;

    public UserDetailsServiceImpl(AuthUserMapper authUserMapper,
                                  AuthRoleMapper authRoleMapper,
                                  AuthPermissionMapper authPermissionMapper,
                                  AuthCacheService cache) {
        this.authUserMapper = authUserMapper;
        this.authRoleMapper = authRoleMapper;
        this.authPermissionMapper = authPermissionMapper;
        this.cache = cache;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 1. 查用户摘要（Redis → DB）
        AuthCacheService.CachedUser cached = cache.getUser(username);
        Long userId;
        String password;
        int status;

        if (cached != null) {
            userId = cached.id();
            password = cached.password();
            status = cached.status();
            log.debug("UserDetails 缓存命中: {}", username);
        } else {
            AuthUser authUser = authUserMapper.selectByUsername(username);
            if (authUser == null) {
                throw new UsernameNotFoundException("用户不存在: " + username);
            }
            userId = authUser.getId();
            password = authUser.getPassword();
            status = authUser.getStatus() != null ? authUser.getStatus() : 1;

            // 写入缓存
            cache.putUser(username,
                    new AuthCacheService.CachedUser(userId, username, password, status));
        }

        if (status != 1) {
            throw new UsernameNotFoundException("用户已被禁用: " + username);
        }

        // 2. 查角色（Redis → DB）
        List<String> roleCodes = cache.getRoles(userId);
        if (roleCodes == null) {
            List<AuthRole> roles = authRoleMapper.selectByUserId(userId);
            roleCodes = roles.stream().map(AuthRole::getRoleCode).toList();
            cache.putRoles(userId, roleCodes);
        }

        // 3. 查权限（Redis → DB）
        List<String> permCodes = cache.getPermissions(userId);
        if (permCodes == null) {
            List<AuthPermission> perms = authPermissionMapper.selectByUserId(userId);
            permCodes = perms.stream().map(AuthPermission::getPermissionCode).toList();
            cache.putPermissions(userId, permCodes);
        }

        // 4. 组装 GrantedAuthority
        Set<SimpleGrantedAuthority> authorities = new HashSet<>();
        for (String rc : roleCodes) {
            authorities.add(new SimpleGrantedAuthority(rc));
        }
        for (String pc : permCodes) {
            authorities.add(new SimpleGrantedAuthority(pc));
        }

        return new User(username, password, status == 1,
                true, true, true, authorities);
    }
}
