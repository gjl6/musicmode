package com.gjl.music.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gjl.music.config.ConfigService;
import com.gjl.music.config.CustomProviderManager;
import com.gjl.music.mapper.CustomProviderMapper;
import com.gjl.music.model.CustomProvider;
import com.gjl.music.model.MusicMetadata;
import com.gjl.music.module.enrich.provider.AbstractCustomProvider;
import com.gjl.music.module.enrich.provider.JavaSourceCompiler;
import com.gjl.music.module.enrich.provider.MusicProvider;
import com.gjl.music.module.enrich.provider.SongProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/custom-providers")
@PreAuthorize("hasAuthority('config:manage')")
public class CustomProviderController {

    private final CustomProviderMapper mapper;
    private final CustomProviderManager manager;
    private final ConfigService configService;
    private final ObjectMapper json = new ObjectMapper();

    private static final String[] TEST_QUERIES = {"生日快乐", "Happy Birthday"};
    private static final String PROVIDER_LIST_KEY = "enrich.default_provider";

    public CustomProviderController(CustomProviderMapper mapper,
                                     CustomProviderManager manager,
                                     ConfigService configService) {
        this.mapper = mapper;
        this.manager = manager;
        this.configService = configService;
    }


    @GetMapping
    public ResponseEntity<List<CustomProvider>> list() {
        return ResponseEntity.ok(mapper.selectAll());
    }


    @GetMapping("/{id}")
    public ResponseEntity<CustomProvider> get(@PathVariable Long id) {
        CustomProvider cp = mapper.selectById(id);
        return cp != null ? ResponseEntity.ok(cp) : ResponseEntity.notFound().build();
    }


    @PostMapping
    public ResponseEntity<?> create(@RequestBody CustomProvider cp) {
        if (cp.getIcon() == null || cp.getIcon().isBlank()) cp.setIcon("🔌");
        if (cp.getVersion() == null || cp.getVersion().isBlank()) cp.setVersion("1.0.0");
        if (cp.getConfigJson() == null) cp.setConfigJson("{}");
                if (cp.getName() == null || cp.getName().isBlank()) {
            cp.setName("custom:" + System.currentTimeMillis());
        }
                CustomProvider dup = mapper.selectByName(cp.getName());
        if (dup != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "名称已存在: " + cp.getName()));
        }
        mapper.insert(cp);
        if (cp.isEnabled()) {
            manager.refresh(cp.getId());
            addToProviderList(cp.getName());
        }
        log.info("Custom provider created: id={}, name={}, mode={}", cp.getId(), cp.getName(), cp.getMode());
        return ResponseEntity.ok(cp);
    }


    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody CustomProvider cp) {
        CustomProvider old = mapper.selectById(id);
        cp.setId(id);
        mapper.update(cp);
                if (old != null && old.isEnabled() != cp.isEnabled()) {
            if (cp.isEnabled()) addToProviderList(cp.getName());
            else removeFromProviderList(cp.getName());
        }
        manager.refresh(id);
        return ResponseEntity.ok(Map.of("success", true));
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        CustomProvider cp = mapper.selectById(id);
        if (cp != null) {
            removeFromProviderList(cp.getName());
        }
        mapper.deleteById(id);
        manager.refreshAll();
        return ResponseEntity.ok(Map.of("success", true));
    }


    private void addToProviderList(String name) {
        String current = configService.getString(PROVIDER_LIST_KEY, "");
        Set<String> set = new LinkedHashSet<>(Arrays.asList(current.split(",")));
        set.add(name.trim());
        configService.updateValue(PROVIDER_LIST_KEY, String.join(",", set));
    }

    private void removeFromProviderList(String name) {
        String current = configService.getString(PROVIDER_LIST_KEY, "");
        Set<String> set = new LinkedHashSet<>(Arrays.asList(current.split(",")));
        set.remove(name.trim());
        configService.updateValue(PROVIDER_LIST_KEY, String.join(",", set));
    }


    @PutMapping("/{id}/params")
    public ResponseEntity<?> updateParams(@PathVariable Long id,
                                           @RequestBody Map<String, String> body) {
        CustomProvider cp = mapper.selectById(id);
        if (cp == null) return ResponseEntity.notFound().build();

        try {
            Map<String, Object> cfg = json.readValue(cp.getConfigJson(),
                    new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> params = (List<Map<String, Object>>) cfg.get("params");

            String paramName = body.get("name");
            String newValue = body.get("value");
            if (params != null && paramName != null) {
                for (Map<String, Object> p : params) {
                    if (paramName.equals(p.get("name"))) {
                        p.put("value", newValue);
                        break;
                    }
                }
            }
            cp.setConfigJson(json.writeValueAsString(cfg));
            mapper.updateConfig(id, cp.getConfigJson(), cp.getSourceCode());
            manager.refresh(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }


    @PostMapping("/{id}/test")
    public ResponseEntity<?> test(@PathVariable Long id) {
        CustomProvider cp = mapper.selectById(id);
        if (cp == null) return ResponseEntity.notFound().build();

        MusicProvider provider = manager.getProvider(id);
        if (provider == null) {
                        try {
                manager.refresh(id);
                provider = manager.getProvider(id);
            } catch (Exception ignored) {}
        }
        if (provider == null) {
            return ResponseEntity.ok(Map.of("success", false, "message", "Provider 未加载"));
        }

        long start = System.currentTimeMillis();
        String hitQuery = null;
        int total = 0;
        String lastError = null;

        if (!(provider instanceof SongProvider sp)) {
            return ResponseEntity.ok(Map.of("success", false, "message", "Provider 不支持歌曲搜索"));
        }

        for (String query : TEST_QUERIES) {
            try {
                List<MusicMetadata> results = sp.search(query, null);
                if (results != null && !results.isEmpty()) {
                    hitQuery = query;
                    total = results.size();
                    break;
                }
            } catch (Exception e) {
                lastError = query + ": " + e.toString();
                log.warn("Custom provider test failed for {}: {}", query, e.toString());
            }
        }

        long latency = System.currentTimeMillis() - start;
        if (hitQuery != null) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "搜索成功，返回 " + total + " 条结果",
                    "detail", "查询词: " + hitQuery,
                    "latencyMs", latency
            ));
        }

                String detail = lastError != null ? "错误: " + lastError : "已尝试: " + String.join(", ", TEST_QUERIES);
        try {
            String le = manager.getLastError(id);
            if (le != null && !le.isBlank()) {
                detail = le;
            }
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "无搜索结果",
                "detail", detail,
                "latencyMs", latency
        ));
    }


    @GetMapping("/template")
    public ResponseEntity<?> downloadTemplate(
            @RequestParam(defaultValue = "MyMusicProvider") String className,
            @RequestParam(defaultValue = "my-provider") String label) {
        String code = JavaSourceCompiler.generateTemplate(className, label);
        return ResponseEntity.ok(Map.of("className", className, "sourceCode", code));
    }


    @PostMapping("/refresh-all")
    public ResponseEntity<?> refreshAll() {
        manager.refreshAll();
        return ResponseEntity.ok(Map.of("success", true, "message", "所有自定义 Provider 已刷新"));
    }
}
