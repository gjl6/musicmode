package com.gjl.music.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.gjl.music.mapper.SystemConfigMapper;
import com.gjl.music.model.SystemConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 系统配置服务 —— Caffeine 缓存 + 事件驱动的热更新。
 *
 * <h3>核心设计</h3>
 * <ul>
 *   <li><b>缓存</b>：Caffeine 30 秒 TTL，平衡实时性与性能</li>
 *   <li><b>热更新</b>：修改时发布 {@link ConfigChangedEvent}，各模块自行监听</li>
 *   <li><b>敏感字段</b>：isSensitive=true 的字段，getForApi 返回 "****"</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 *   // 方式一：每次实时读取（推荐，自动享受缓存）
 *   int batchSize = configService.getInt("dedup.hash.batch_size", 500);
 *
 *   // 方式二：监听变更事件热刷新内部状态
 *   @EventListener
 *   public void onConfigChanged(ConfigChangedEvent e) {
 *       if (e.configKey().equals("enrich.qqmusic.rate_limit_ms")) {
 *           this.limiter.updateInterval(e.asLong());
 *       }
 *   }
 * }</pre>
 */
@Slf4j
@Service
public class ConfigService {

    private final SystemConfigMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    /** 配置值缓存（30 秒 TTL） */
    private Cache<String, String> valueCache;

    /** 完整的 SystemConfig 缓存（供列表/搜索使用，60 秒 TTL） */
    private volatile List<SystemConfig> listCache;
    private volatile long listCacheExpireAt;

    private static final long LIST_CACHE_TTL_MS = 60_000;

    /** API 不暴露的 key 前缀（安全/内部配置，仅供后端代码读取） */
    private static final Set<String> API_HIDDEN_PREFIXES = Set.of(
            "music.auth.",         // JWT 密钥、管理员密码等安全配置
            "fingerprint.",        // 指纹计算参数
            "dedup.",              // 去重策略参数
            "organize.",           // 文件组织参数
            "music.ffmpeg.",       // FFmpeg 路径
            "music.ffprobe."       // ffprobe 路径
    );

    public ConfigService(SystemConfigMapper mapper, ApplicationEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    void init() {
        this.valueCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(30))
                .maximumSize(1000)
                .build();
        log.info("ConfigService initialized with Caffeine cache (TTL=30s)");
    }

    // ═══════════════════════════════════════════════════════════════
    // 读取方法（类型安全）
    // ═══════════════════════════════════════════════════════════════

    public String getString(String key, String defaultValue) {
        String cached = valueCache.getIfPresent(key);
        if (cached != null) return cached;

        SystemConfig config = mapper.selectByKey(key);
        String value = (config != null) ? config.getConfigValue() : defaultValue;
        // Caffeine does not allow null values; skip caching to avoid NPE
        if (value != null) {
            valueCache.put(key, value);
        }
        return value;
    }

    public int getInt(String key, int defaultValue) {
        try { return Integer.parseInt(getString(key, String.valueOf(defaultValue))); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    public long getLong(String key, long defaultValue) {
        try { return Long.parseLong(getString(key, String.valueOf(defaultValue))); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    public double getDouble(String key, double defaultValue) {
        try { return Double.parseDouble(getString(key, String.valueOf(defaultValue))); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String v = getString(key, String.valueOf(defaultValue));
        return "true".equalsIgnoreCase(v);
    }

    /** 获取逗号分隔列表 */
    public List<String> getList(String key, List<String> defaultValue) {
        String v = getString(key, null);
        if (v == null || v.isBlank()) return defaultValue;
        return Arrays.stream(v.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════
    // 写入方法
    // ═══════════════════════════════════════════════════════════════

    /**
     * 更新配置值。立即写入 DB、刷新缓存、发布事件。
     */
    public void updateValue(String key, String newValue) {
        String oldValue = getString(key, null);
        mapper.updateValue(key, newValue);
        valueCache.put(key, newValue);
        invalidateListCache();
        log.info("Config updated: {} = {}", key, newValue);
        eventPublisher.publishEvent(new ConfigChangedEvent(key, oldValue, newValue));
    }

    // ═══════════════════════════════════════════════════════════════
    // 批量查询（供前端使用）
    // ═══════════════════════════════════════════════════════════════

    /** 获取所有配置（含缓存，敏感/内部配置自动过滤） */
    public List<SystemConfig> getAllForApi() {
        List<SystemConfig> list = getListCache();
        return filterApiHidden(maskSensitive(list));
    }

    /** 按分类查询 */
    public List<SystemConfig> getByCategoryForApi(String category) {
        List<SystemConfig> list;
        if (category == null || category.isBlank()) {
            list = getListCache();
        } else {
            list = mapper.selectByCategory(category);
        }
        return filterApiHidden(maskSensitive(list));
    }

    /** 搜索（按 key 或 label） */
    public List<SystemConfig> searchForApi(String keyword) {
        if (keyword == null || keyword.isBlank()) return getAllForApi();
        List<SystemConfig> list = mapper.search(keyword);
        return filterApiHidden(maskSensitive(list));
    }

    /** 获取单个配置（内部配置返回 null） */
    public SystemConfig getByKeyForApi(String key) {
        if (isApiHidden(key)) return null;
        SystemConfig config = mapper.selectByKey(key);
        if (config != null && config.isSensitive()) {
            config.setConfigValue("****");
        }
        return config;
    }

    /** 获取所有分类名 */
    public List<String> getAllCategories() {
        return mapper.selectAllCategories();
    }

    /** 刷新缓存 */
    public void refreshCache() {
        valueCache.invalidateAll();
        invalidateListCache();
        log.info("Config cache refreshed");
    }

    // ═══════════════════════════════════════════════════════════════
    // 内部方法
    // ═══════════════════════════════════════════════════════════════

    private List<SystemConfig> maskSensitive(List<SystemConfig> list) {
        if (list == null) return Collections.emptyList();
        for (SystemConfig c : list) {
            if (c.isSensitive()) {
                c.setConfigValue("****");
            }
        }
        return list;
    }

    /** 过滤不应通过 API 暴露的内部/安全配置 */
    private List<SystemConfig> filterApiHidden(List<SystemConfig> list) {
        if (list == null) return Collections.emptyList();
        return list.stream()
                .filter(c -> !isApiHidden(c.getConfigKey()))
                .collect(Collectors.toList());
    }

    private boolean isApiHidden(String key) {
        if (key == null) return false;
        for (String prefix : API_HIDDEN_PREFIXES) {
            if (key.startsWith(prefix)) return true;
        }
        return false;
    }

    private List<SystemConfig> getListCache() {
        long now = System.currentTimeMillis();
        if (listCache != null && now < listCacheExpireAt) {
            return listCache;
        }
        listCache = mapper.selectAll();
        listCacheExpireAt = now + LIST_CACHE_TTL_MS;
        return listCache;
    }

    private void invalidateListCache() {
        listCacheExpireAt = 0;
    }
}
