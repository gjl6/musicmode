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


@Slf4j
@Service
public class ConfigService {

    private final SystemConfigMapper mapper;
    private final ApplicationEventPublisher eventPublisher;


    private Cache<String, String> valueCache;


    private volatile List<SystemConfig> listCache;
    private volatile long listCacheExpireAt;

    private static final long LIST_CACHE_TTL_MS = 60_000;

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


    public String getString(String key, String defaultValue) {
        String cached = valueCache.getIfPresent(key);
        if (cached != null) return cached;

        SystemConfig config = mapper.selectByKey(key);
        String value = (config != null) ? config.getConfigValue() : defaultValue;
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


    public List<String> getList(String key, List<String> defaultValue) {
        String v = getString(key, null);
        if (v == null || v.isBlank()) return defaultValue;
        return Arrays.stream(v.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }


    public void updateValue(String key, String newValue) {
        String oldValue = getString(key, null);
        mapper.updateValue(key, newValue);
        valueCache.put(key, newValue);
        invalidateListCache();
        log.info("Config updated: {} = {}", key, newValue);
        eventPublisher.publishEvent(new ConfigChangedEvent(key, oldValue, newValue));
    }


    public List<SystemConfig> getAllForApi() {
        List<SystemConfig> list = getListCache();
        return maskSensitive(list);
    }


    public List<SystemConfig> getByCategoryForApi(String category) {
        List<SystemConfig> list;
        if (category == null || category.isBlank()) {
            list = getListCache();
        } else {
            list = mapper.selectByCategory(category);
        }
        return maskSensitive(list);
    }


    public List<SystemConfig> searchForApi(String keyword) {
        if (keyword == null || keyword.isBlank()) return getAllForApi();
        List<SystemConfig> list = mapper.search(keyword);
        return maskSensitive(list);
    }


    public SystemConfig getByKeyForApi(String key) {
        SystemConfig config = mapper.selectByKey(key);
        if (config != null && config.isSensitive()) {
            config.setConfigValue("****");
        }
        return config;
    }


    public List<String> getAllCategories() {
        return mapper.selectAllCategories();
    }


    public void refreshCache() {
        valueCache.invalidateAll();
        invalidateListCache();
        log.info("Config cache refreshed");
    }


    private List<SystemConfig> maskSensitive(List<SystemConfig> list) {
        if (list == null) return Collections.emptyList();
        for (SystemConfig c : list) {
            if (c.isSensitive()) {
                c.setConfigValue("****");
            }
        }
        return list;
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
