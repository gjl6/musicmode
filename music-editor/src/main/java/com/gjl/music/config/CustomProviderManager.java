package com.gjl.music.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gjl.music.mapper.CustomProviderMapper;
import com.gjl.music.model.CustomProvider;
import com.gjl.music.module.enrich.provider.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


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


    public void refreshAll() {
        for (Long id : Set.copyOf(loaded.keySet())) unload(id);
        loadAll();
    }


    public List<CustomProvider> getLoadedRecords() {
        return List.copyOf(records.values());
    }


    public MusicProvider getProvider(Long id) {
        return loaded.get(id);
    }


    public String getLabel(String name) {
        for (CustomProvider cp : records.values()) {
            if (name.equals(cp.getName())) return cp.getLabel();
        }
        return null;
    }


    public List<MusicProvider> getLoadedProviders() {
        return List.copyOf(loaded.values());
    }


    public String getLastError(Long id) {
        AbstractCustomProvider raw = rawInstances.get(id);
        return raw != null ? raw.getLastError() : null;
    }


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
                        String className = extractClassName(sourceCode);
            JavaSourceCompiler compiler = new JavaSourceCompiler();
            Class<?> clazz = compiler.compile(className, sourceCode);

            if (!AbstractCustomProvider.class.isAssignableFrom(clazz)) {
                throw new RuntimeException("Class must extend AbstractCustomProvider");
            }

            AbstractCustomProvider instance = (AbstractCustomProvider) clazz
                    .getDeclaredConstructor().newInstance();

                        Map<String, String> configValues = loadConfigValues(cp);
            instance.setConfigValues(configValues);

                        String dbName = cp.getName();
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
                var m = java.util.regex.Pattern.compile("public\\s+class\\s+(\\w+)").matcher(sourceCode);
        if (m.find()) {
            String simpleName = m.group(1);
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
