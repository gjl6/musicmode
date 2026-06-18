package com.gjl.music.controller;

import com.gjl.music.model.WatchRule;
import com.gjl.music.service.watch.WatchRuleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.*;


@RestController
@RequestMapping("/api/watch")
@PreAuthorize("hasAuthority('config:manage')")
public class WatchRuleController {

    private final WatchRuleService ruleService;

    public WatchRuleController(WatchRuleService ruleService) {
        this.ruleService = ruleService;
    }


    @GetMapping("/profiles")
    public List<WatchRule> list() {
        return ruleService.listAll();
    }

    @GetMapping("/profiles/{id}")
    public WatchRule get(@PathVariable Long id) {
        return ruleService.getById(id);
    }

    @PostMapping("/profiles")
    public WatchRule create(@RequestBody WatchRule rule) {
        return ruleService.create(rule);
    }

    @PutMapping("/profiles/{id}")
    public WatchRule update(@PathVariable Long id, @RequestBody WatchRule rule) {
        rule.setId(id);
        return ruleService.update(rule);
    }

    @DeleteMapping("/profiles/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        ruleService.delete(id);
        return Map.of("success", true);
    }


    @PostMapping("/profiles/{id}/execute")
    public Map<String, Object> execute(@PathVariable Long id,
                                        @RequestBody Map<String, List<String>> body) {
        List<String> paths = body.getOrDefault("paths", List.of());
        if (paths.isEmpty()) {
            return Map.of("success", false, "error", "paths is required");
        }
        List<Path> filePaths = paths.stream().map(Path::of).toList();
        String pipelineId = ruleService.submitBatch(id, filePaths);
        return Map.of("success", true, "pipelineId", pipelineId != null ? pipelineId : "");
    }


    @GetMapping("/steps")
    public List<Map<String, Object>> steps() {
        return List.of(
                stepInfo("encoding-repair", "乱码修复", "修复标签中的乱码文本",
                        Map.of("fields", "要修复的字段列表，默认 all")),
                stepInfo("chinese-convert", "繁简转换", "繁体中文转简体（或反向）",
                        Map.of("direction", "toSimplified 或 toTraditional")),
                stepInfo("enrich", "元数据刮削", "从在线源搜索并增强元数据",
                        Map.of("provider", "搜索源（逗号分隔），如 qqmusic,kugou",
                               "matchMode", "匹配模式：LOOSE / STRICT",
                               "mergeScope", "合并范围：FILL_ONLY / REPLACE_ALL")),
                stepInfo("fingerprint", "声纹识别", "计算 Chromaprint 音频指纹",
                        Map.of()),
                stepInfo("organize", "文件整理", "按元数据层级重组文件目录",
                        Map.of("pattern", "目录层级，如 artist/album",
                               "mode", "move 或 copy")),
                stepInfo("format-convert", "格式转换", "转码音频格式（如 FLAC→MP3）",
                        Map.of("targetFormat", "目标格式，如 mp3"))
        );
    }

    private Map<String, Object> stepInfo(String name, String label, String desc,
                                          Map<String, String> configKeys) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", name);
        info.put("label", label);
        info.put("description", desc);
        info.put("configKeys", configKeys);
        return info;
    }
}
