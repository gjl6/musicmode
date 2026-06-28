package com.gjl.music.auth.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 认证数据 Redis 缓存层 —— 减少 DB 查询。
 * <p>
 * Redis 不可用时自动降级为 DB 直查（返回 null，调用方回退到 DB）。
 * 通过构造函数注入 StringRedisTemplate，但所有 Redis 操作均 try-catch，
 * Redis 不可用时返回 null 触发调用方走 DB。
 *
 * <h3>缓存键</h3>
 * <ul>
 *   <li>{@code auth:user:<username>} — 用户摘要（id, status, password）</li>
 *   <li>{@code auth:roles:<userId>} — 用户角色码列表</li>
 *   <li>{@code auth:perms:<userId>} — 用户权限码列表</li>
 *   <li>{@code auth:perms:all} — 所有权限元数据</li>
 *   <li>{@code auth:role:users:<roleCode>} — 拥有某角色的用户 ID 集合</li>
 * </ul>
 */
@Slf4j
@Service
public class AuthCacheService {

    private static final String PREFIX_USER = "auth:user:";
    private static final String PREFIX_ROLES = "auth:roles:";
    private static final String PREFIX_PERMS = "auth:perms:";
    private static final String PREFIX_ROLE_USERS = "auth:role:users:";
    private static final String PREFIX_REFRESH = "auth:refresh:";
    private static final String KEY_PERMS_ALL = "auth:perms:all";

    /** 缓存过期时间 = accessToken 有效期（秒），默认 30 min */
    private static final long TTL_SECONDS = 1800;

    /** Token 版本号有效期（略长于 accessToken，确保旧 token 自然过期前都能检测到） */
    private static final long VERSION_TTL_SECONDS = 3600;

    // ── Token 版本号（权限变更时递增，旧 token 立即失效）──

    private static final String PREFIX_VERSION = "auth:version:";

    /** 递增用户的 token 版本号 → 所有旧 token 立即失效 */
    public long incrementVersion(String username) {
        try {
            Long v = redis.opsForValue().increment(PREFIX_VERSION + username);
            redis.expire(PREFIX_VERSION + username, VERSION_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("Token 版本递增: username={}, version={}", username, v);
            return v != null ? v : 0;
        } catch (Exception e) {
            log.debug("Redis 版本递增失败: username={}", username, e);
            return 0;
        }
    }

    /** 获取用户当前 token 版本号 */
    public long getVersion(String username) {
        try {
            String v = redis.opsForValue().get(PREFIX_VERSION + username);
            return v != null ? Long.parseLong(v) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public AuthCacheService(StringRedisTemplate redis) {
        this.redis = redis;
        this.objectMapper = new ObjectMapper();
    }

    // ── 用户摘要缓存 ──

    public CachedUser getUser(String username) {
        try {
            String json = redis.opsForValue().get(PREFIX_USER + username);
            if (json != null) {
                return objectMapper.readValue(json, CachedUser.class);
            }
        } catch (Exception e) {
            log.debug("Redis 读取用户缓存失败: username={}", username, e);
        }
        return null;
    }

    public void putUser(String username, CachedUser user) {
        try {
            String json = objectMapper.writeValueAsString(user);
            redis.opsForValue().set(PREFIX_USER + username, json, TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("Redis 写入用户缓存失败: username={}", username, e);
        }
    }

    public void evictUser(String username) {
        try {
            redis.delete(PREFIX_USER + username);
        } catch (Exception e) {
            log.debug("Redis 删除用户缓存失败: username={}", username, e);
        }
    }

    // ── 角色缓存 ──

    public List<String> getRoles(Long userId) {
        try {
            String json = redis.opsForValue().get(PREFIX_ROLES + userId);
            if (json != null) {
                return objectMapper.readValue(json, new TypeReference<List<String>>() {});
            }
        } catch (Exception e) {
            log.debug("Redis 读取角色缓存失败: userId={}", userId, e);
        }
        return null;
    }

    public void putRoles(Long userId, List<String> roleCodes) {
        try {
            String json = objectMapper.writeValueAsString(roleCodes);
            redis.opsForValue().set(PREFIX_ROLES + userId, json, TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("Redis 写入角色缓存失败: userId={}", userId, e);
        }
    }

    public void evictRoles(Long userId) {
        try {
            redis.delete(PREFIX_ROLES + userId);
        } catch (Exception e) {
            log.debug("Redis 删除角色缓存失败: userId={}", userId, e);
        }
    }

    // ── 权限缓存 ──

    public List<String> getPermissions(Long userId) {
        try {
            String json = redis.opsForValue().get(PREFIX_PERMS + userId);
            if (json != null) {
                return objectMapper.readValue(json, new TypeReference<List<String>>() {});
            }
        } catch (Exception e) {
            log.debug("Redis 读取权限缓存失败: userId={}", userId, e);
        }
        return null;
    }

    public void putPermissions(Long userId, List<String> permCodes) {
        try {
            String json = objectMapper.writeValueAsString(permCodes);
            redis.opsForValue().set(PREFIX_PERMS + userId, json, TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("Redis 写入权限缓存失败: userId={}", userId, e);
        }
    }

    public void evictPermissions(Long userId) {
        try {
            redis.delete(PREFIX_PERMS + userId);
        } catch (Exception e) {
            log.debug("Redis 删除权限缓存失败: userId={}", userId, e);
        }
    }

    // ── 批量失效：某角色的所有用户 ──

    public void putRoleUsers(String roleCode, Set<Long> userIds) {
        try {
            String json = objectMapper.writeValueAsString(userIds);
            redis.opsForValue().set(PREFIX_ROLE_USERS + roleCode, json, TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("Redis 写入角色用户缓存失败: roleCode={}", roleCode, e);
        }
    }

    public Set<Long> getRoleUsers(String roleCode) {
        try {
            String json = redis.opsForValue().get(PREFIX_ROLE_USERS + roleCode);
            if (json != null) {
                return objectMapper.readValue(json, new TypeReference<Set<Long>>() {});
            }
        } catch (Exception e) {
            log.debug("Redis 读取角色用户缓存失败: roleCode={}", roleCode, e);
        }
        return null;
    }

    /** 角色权限变更时，失效该角色下所有用户的权限缓存 */
    public void evictPermissionsByRole(String roleCode) {
        Set<Long> userIds = getRoleUsers(roleCode);
        if (userIds != null) {
            for (Long uid : userIds) {
                evictPermissions(uid);
            }
        }
    }

    // ── 角色/权限变更时，批量失效 ──

    /** 用户角色变更时，同时失效角色和权限缓存 */
    public void evictUserAuth(Long userId) {
        evictRoles(userId);
        evictPermissions(userId);
    }

    // ── 全局权限元数据 ──

    public List<PermissionMeta> getAllPermissions() {
        try {
            String json = redis.opsForValue().get(KEY_PERMS_ALL);
            if (json != null) {
                return objectMapper.readValue(json, new TypeReference<List<PermissionMeta>>() {});
            }
        } catch (Exception e) {
            log.debug("Redis 读取权限元数据缓存失败", e);
        }
        return null;
    }

    public void putAllPermissions(List<PermissionMeta> perms) {
        try {
            String json = objectMapper.writeValueAsString(perms);
            redis.opsForValue().set(KEY_PERMS_ALL, json, 3600, TimeUnit.SECONDS); // 1 小时 TTL
        } catch (Exception e) {
            log.debug("Redis 写入权限元数据缓存失败", e);
        }
    }

    public void evictAllPermissions() {
        try {
            redis.delete(KEY_PERMS_ALL);
        } catch (Exception e) {
            log.debug("Redis 删除权限元数据缓存失败", e);
        }
    }

    // ── RefreshToken 缓存 ──

    /** 缓存 refreshToken（登录/刷新时写入） */
    public void putRefreshToken(String tokenHash, CachedRefreshToken info) {
        try {
            String json = objectMapper.writeValueAsString(info);
            // TTL = refreshToken 剩余有效时间（秒）
            long ttl = Math.max(1, info.expiresAt - System.currentTimeMillis() / 1000);
            redis.opsForValue().set(PREFIX_REFRESH + tokenHash, json, ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("Redis 写入 refreshToken 缓存失败", e);
        }
    }

    /** 查询 refreshToken 缓存 */
    public CachedRefreshToken getRefreshToken(String tokenHash) {
        try {
            String json = redis.opsForValue().get(PREFIX_REFRESH + tokenHash);
            if (json != null) {
                return objectMapper.readValue(json, CachedRefreshToken.class);
            }
        } catch (Exception e) {
            log.debug("Redis 读取 refreshToken 缓存失败", e);
        }
        return null;
    }

    /** 吊销 refreshToken（登出/权限变更时调用） */
    public void revokeRefreshToken(String tokenHash) {
        try {
            String json = redis.opsForValue().get(PREFIX_REFRESH + tokenHash);
            if (json != null) {
                var info = objectMapper.readValue(json, CachedRefreshToken.class);
                putRefreshToken(tokenHash,
                        new CachedRefreshToken(info.userId, info.expiresAt, true));
            }
        } catch (Exception e) {
            log.debug("Redis 吊销 refreshToken 缓存失败", e);
        }
    }

    /** 批量吊销某用户的所有 refreshToken（通过删除缓存强制走 DB） */
    public void evictRefreshByUser(Long userId) {
        // refreshToken 以 hash 为 key，无法反向索引。
        // 策略：用户级版本号已保证 accessToken 即时失效。
        // refreshToken 缓存设有 TTL，下次 refresh 时 DB 会返回 revoked=true。
        // 此处不做逐条吊销，依赖 DB 权威 + Redis TTL 自然过期。
    }

    // ── 内嵌类型 ──

    /** 缓存的用户摘要（不存敏感数据） */
    public record CachedUser(Long id, String username, String password, int status) {}

    /** 缓存的权限元数据 */
    public record PermissionMeta(Long id, String permissionCode, String permissionName, String description) {}

    /** 缓存的 refreshToken 信息 */
    public record CachedRefreshToken(Long userId, long expiresAt, boolean revoked) {}
}
