package com.gjl.music.auth.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
public class AuthCacheService {

    private static final String PREFIX_USER = "auth:user:";
    private static final String PREFIX_ROLES = "auth:roles:";
    private static final String PREFIX_PERMS = "auth:perms:";
    private static final String PREFIX_ROLE_USERS = "auth:role:users:";
    private static final String PREFIX_REFRESH = "auth:refresh:";
    private static final String KEY_PERMS_ALL = "auth:perms:all";


    private static final long TTL_SECONDS = 1800;


    private static final long VERSION_TTL_SECONDS = 3600;


    private static final String PREFIX_VERSION = "auth:version:";


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


    public void evictPermissionsByRole(String roleCode) {
        Set<Long> userIds = getRoleUsers(roleCode);
        if (userIds != null) {
            for (Long uid : userIds) {
                evictPermissions(uid);
            }
        }
    }


    public void evictUserAuth(Long userId) {
        evictRoles(userId);
        evictPermissions(userId);
    }


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
            redis.opsForValue().set(KEY_PERMS_ALL, json, 3600, TimeUnit.SECONDS);
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


    public void putRefreshToken(String tokenHash, CachedRefreshToken info) {
        try {
            String json = objectMapper.writeValueAsString(info);
                        long ttl = Math.max(1, info.expiresAt - System.currentTimeMillis() / 1000);
            redis.opsForValue().set(PREFIX_REFRESH + tokenHash, json, ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("Redis 写入 refreshToken 缓存失败", e);
        }
    }


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


    public void evictRefreshByUser(Long userId) {
                                    }


    public record CachedUser(Long id, String username, String password, int status) {}


    public record PermissionMeta(Long id, String permissionCode, String permissionName, String description) {}


    public record CachedRefreshToken(Long userId, long expiresAt, boolean revoked) {}
}
