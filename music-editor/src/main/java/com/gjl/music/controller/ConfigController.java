package com.gjl.music.controller;

import com.gjl.music.config.ConfigService;
import com.gjl.music.module.enrich.provider.SongProvider;
import com.gjl.music.model.MusicMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.*;


@Slf4j
@RestController
@RequestMapping("/api/config")
@PreAuthorize("hasAuthority('config:manage')")
public class ConfigController {

    private final ConfigService configService;
    private final Map<String, SongProvider> providerMap;

    private static final String[] TEST_QUERIES = {
        "生日快乐", "Happy Birthday"
    };

    public ConfigController(ConfigService configService,
                            List<SongProvider> providers) {
        this.configService = configService;
        this.providerMap = new HashMap<>();
        for (var p : providers) {
            providerMap.put(p.name(), p);
        }
    }


    @GetMapping
    public ResponseEntity<List<?>> list(@RequestParam(required = false) String category,
                                        @RequestParam(required = false) String search) {
        if (search != null && !search.isBlank()) {
            return ResponseEntity.ok(configService.searchForApi(search));
        }
        return ResponseEntity.ok(configService.getByCategoryForApi(category));
    }


    @GetMapping("/{key}")
    public ResponseEntity<?> getByKey(@PathVariable String key) {
        var config = configService.getByKeyForApi(key);
        if (config == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(config);
    }


    @PutMapping("/{key}")
    public ResponseEntity<?> update(@PathVariable String key,
                                    @RequestBody Map<String, String> body) {
        String newValue = body.get("value");
        if (newValue == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "缺少 value 字段"));
        }
        try {
            configService.updateValue(key, newValue);
            return ResponseEntity.ok(Map.of("success", true, "key", key));
        } catch (Exception e) {
            log.error("Failed to update config: {} = {}", key, newValue, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }


    @PostMapping("/refresh")
    public ResponseEntity<?> refresh() {
        configService.refreshCache();
        return ResponseEntity.ok(Map.of("success", true));
    }


    @GetMapping("/categories")
    public ResponseEntity<List<String>> categories() {
        return ResponseEntity.ok(configService.getAllCategories());
    }


    @GetMapping("/provider-test")
    public ResponseEntity<?> testProvider(@RequestParam String provider) {
        SongProvider p = providerMap.get(provider);
        if (p == null) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Provider 不存在",
                    "detail", "未知的标签源: " + provider
            ));
        }

        long start = System.currentTimeMillis();
        String hitQuery = null;
        int totalResults = 0;

        for (String query : TEST_QUERIES) {
            try {
                List<MusicMetadata> results = p.search(query, null);
                if (results != null && !results.isEmpty()) {
                    hitQuery = query;
                    totalResults = results.size();
                    break;
                }
            } catch (Exception ignored) {
                            }
        }

        long latency = System.currentTimeMillis() - start;

        if (hitQuery != null) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "搜索成功，返回 " + totalResults + " 条结果",
                    "detail", "查询词: " + hitQuery,
                    "latencyMs", latency
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "无搜索结果",
                    "detail", "已尝试: " + String.join(", ", TEST_QUERIES),
                    "latencyMs", latency
            ));
        }
    }
}
