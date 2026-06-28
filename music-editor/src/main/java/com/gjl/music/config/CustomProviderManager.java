package com.gjl.music.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gjl.music.editor.mapper.CustomProviderMapper;
import com.gjl.music.model.CustomProvider;
import com.gjl.music.module.AbstractCustomProvider;
import com.gjl.music.module.BridgeHttpProvider;
import com.gjl.music.module.JavaSourceCompiler;
import com.gjl.music.module.MusicProvider;
import com.gjl.music.module.song.enrich.SongProvider;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义 Provider 生命周期管理器。
 *
 * <h3>功能</h3>
 * <ul>
 *   <li>启动时从 DB 加载所有 enabled 的 custom_provider</li>
 *   <li>BRIDGE 模式：创建 {@link BridgeHttpProvider}</li>
 *   <li>JAVA 模式：编译源码 → 实例化 → 注入配置值</li>
 *   <li>动态注册为 Spring Bean，{@code AggregatedProvider} 自动发现</li>
 *   <li>支持热刷新</li>
 * </ul>
 */
@Slf4j
@Component
public class CustomProviderManager {

    private final CustomProviderMapper mapper;
    private final DefaultListableBeanFactory beanFactory;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<Long, MusicProvider> loaded = new ConcurrentHashMap<>();
    private final Map<Long, CustomProvider> records = new ConcurrentHashMap<>();
    private final Map<Long, AbstractCustomProvider> rawInstances = new ConcurrentHashMap<>();

    public CustomProviderManager(CustomProviderMapper mapper,
                                  ConfigurableApplicationContext ctx) {
        this.mapper = mapper;
        this.beanFactory = (DefaultListableBeanFactory) ctx.getBeanFactory();
    }

    @PostConstruct
    public void loadAll() {
        List<CustomProvider> list = mapper.selectEnabled();
        log.info("Loading {} custom provider(s)...", list.size());
        for (CustomProvider cp : list) {
            try {
                MusicProvider provider = createProvider(cp);
                if (provider != null) {
                    register(cp, provider);
                    log.info("Custom provider loaded: {} (mode={})", cp.getName(), cp.getMode());
                }
            } catch (Exception e) {
                log.error("Failed to load custom provider: {} (mode={})", cp.getName(), cp.getMode(), e);
            }
        }
    }

    /** 刷新单个 provider */
    public void refresh(Long id) {
        CustomProvider cp = mapper.selectById(id);
        if (cp == null) return;
        unload(id);
        if (cp.isEnabled()) {
            MusicProvider provider = createProvider(cp);
            if (provider != null) {
                register(cp, provider);
                log.info("Custom provider refreshed: {}", cp.getName());
            }
        }
    }

    /** 刷新所有 */
    public void refreshAll() {
        for (Long id : Set.copyOf(loaded.keySet())) unload(id);
        loadAll();
    }

    /** 获取加载的 provider 记录 */
    public List<CustomProvider> getLoadedRecords() {
        return List.copyOf(records.values());
    }

    /** 获取 provider 实例（供 Controller 测试） */
    public MusicProvider getProvider(Long id) {
        return loaded.get(id);
    }

    /** 根据 provider name 查找友好标签 */
    public String getLabel(String name) {
        for (CustomProvider cp : records.values()) {
            if (name.equals(cp.getName())) return cp.getLabel();
        }
        return null;
    }

    /** 获取所有已加载的 provider 实例（供 EnrichSearcherImpl 合并使用） */
    public List<MusicProvider> getLoadedProviders() {
        return List.copyOf(loaded.values());
    }

    /** 获取指定 provider 的最后错误信息（供测试端点使用） */
    public String getLastError(Long id) {
        AbstractCustomProvider raw = rawInstances.get(id);
        return raw != null ? raw.getLastError() : null;
    }

    // ── 内部 ──

    public MusicProvider createProvider(CustomProvider cp) {
        return switch (cp.getMode().toUpperCase()) {
            case "BRIDGE" -> createBridgeProvider(cp);
            case "JAVA" -> createJavaProvider(cp);
            default -> throw new IllegalArgumentException("Unknown mode: " + cp.getMode());
        };
    }

    private SongProvider createBridgeProvider(CustomProvider cp) {
        try {
            Map<String, Object> cfg = json.readValue(cp.getConfigJson(),
                    new TypeReference<Map<String, Object>>() {});
            String bridgeUrl = (String) cfg.getOrDefault("bridgeUrl", "");
            String authHeader = (String) cfg.getOrDefault("authHeader", "");
            int timeout = cfg.get("timeoutSeconds") instanceof Number n ? n.intValue() : 15;
            long rateLimitMs = cfg.get("rateLimitMs") instanceof Number n ? n.longValue() : 240;
            int rateLimitRetries = cfg.get("rateLimitRetries") instanceof Number n ? n.intValue() : 3;
            long rateLimitBackoffMs = cfg.get("rateLimitBackoffMs") instanceof Number n ? n.longValue() : 30000;
            return new BridgeHttpProvider(cp.getName(), cp.getLabel(),
                    bridgeUrl, authHeader, timeout,
                    rateLimitMs, rateLimitRetries, rateLimitBackoffMs);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse bridge config for " + cp.getName(), e);
        }
    }

    private SongProvider createJavaProvider(CustomProvider cp) {
        String sourceCode = cp.getSourceCode();
        if (sourceCode == null || sourceCode.isBlank()) {
            log.warn("Skipping JAVA provider {} (id={}): no source code uploaded yet", cp.getName(), cp.getId());
            return null;
        }
        try {
            // 提取类名
            String className = extractClassName(sourceCode);
            JavaSourceCompiler compiler = new JavaSourceCompiler();
            Class<?> clazz = compiler.compile(className, sourceCode);

            if (!AbstractCustomProvider.class.isAssignableFrom(clazz)) {
                throw new RuntimeException("Class must extend AbstractCustomProvider");
            }

            AbstractCustomProvider instance = (AbstractCustomProvider) clazz
                    .getDeclaredConstructor().newInstance();

            // 注入配置值
            Map<String, String> configValues = loadConfigValues(cp);
            instance.setConfigValues(configValues);

            // 用 DB 记录的 name 覆盖源码中的 name()，保证与 enrich.default_provider 一致
            String dbName = cp.getName();
            // 保存原始实例引用，供 getLastError() 使用
            rawInstances.put(cp.getId(), instance);
            return new SongProvider() {
                @Override public String name() { return dbName; }
                @Override public List<com.gjl.music.model.MusicMetadata> search(String t, String a) { return instance.search(t, a); }
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to compile JAVA provider: " + cp.getName(), e);
        }
    }

    private Map<String, String> loadConfigValues(CustomProvider cp) {
        Map<String, String> values = new LinkedHashMap<>();
        try {
            Map<String, Object> cfg = json.readValue(cp.getConfigJson(),
                    new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> params = (List<Map<String, Object>>) cfg.get("params");
            if (params != null) {
                for (Map<String, Object> p : params) {
                    String name = (String) p.get("name");
                    String value = (String) p.getOrDefault("value", "");
                    if (name != null) values.put(name, value);
                }
            }
        } catch (Exception ignored) {}
        return values;
    }

    private String extractClassName(String sourceCode) {
        // 简单正则提取 public class Xxx
        var m = java.util.regex.Pattern.compile("public\\s+class\\s+(\\w+)").matcher(sourceCode);
        if (m.find()) {
            String simpleName = m.group(1);
            // 查找 package 声明
            var pm = java.util.regex.Pattern.compile("package\\s+([\\w.]+)\\s*;").matcher(sourceCode);
            if (pm.find()) {
                return pm.group(1) + "." + simpleName;
            }
            return simpleName;
        }
        throw new RuntimeException("Cannot determine class name from source");
    }

    private void register(CustomProvider cp, MusicProvider provider) {
        String beanName = "customProvider_" + cp.getId();
        if (beanFactory.containsSingleton(beanName)) {
            beanFactory.destroySingleton(beanName);
        }
        beanFactory.registerSingleton(beanName, provider);
        loaded.put(cp.getId(), provider);
        records.put(cp.getId(), cp);
    }

    private void unload(Long id) {
        String beanName = "customProvider_" + id;
        if (beanFactory.containsSingleton(beanName)) {
            beanFactory.destroySingleton(beanName);
        }
        loaded.remove(id);
        records.remove(id);
        rawInstances.remove(id);
    }
}
