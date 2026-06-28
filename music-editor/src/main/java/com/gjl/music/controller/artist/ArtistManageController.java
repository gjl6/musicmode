package com.gjl.music.controller.artist;

import com.gjl.music.model.Artist;
import com.gjl.music.module.artist.enrich.ArtistEnrichPipeline;
import com.gjl.music.module.artist.enrich.ArtistEnrichWriter;
import com.gjl.music.module.artist.artistmerge.MergeGroup;
import com.gjl.music.module.artist.enrich.provider.ArtistInfo;
import com.gjl.music.service.artist.ArtistManageService;
import com.gjl.music.service.song.PipelineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 艺术家管理 REST API — 列表/搜索 + 重复检测 + 合并提交 + 单艺术家增强预览/应用。
 */
@Slf4j
@RestController
@RequestMapping("/api/editor/artists")
@PreAuthorize("hasAuthority('artist:write')")
public class ArtistManageController {

    private final ArtistManageService artistManageService;
    private final PipelineService pipelineService;
    private final ArtistEnrichPipeline artistEnrichPipeline;
    private final ArtistEnrichWriter artistEnrichWriter;

    public ArtistManageController(ArtistManageService artistManageService,
                                  PipelineService pipelineService,
                                  ArtistEnrichPipeline artistEnrichPipeline,
                                  ArtistEnrichWriter artistEnrichWriter) {
        this.artistManageService = artistManageService;
        this.pipelineService = pipelineService;
        this.artistEnrichPipeline = artistEnrichPipeline;
        this.artistEnrichWriter = artistEnrichWriter;
    }

    /**
     * GET /api/editor/artists — 分页搜索艺术家列表（支持 mode 选择和叠加过滤）。
     */
    @GetMapping
    public ResponseEntity<?> listArtists(
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String letter,
            @RequestParam(required = false) Integer minSongs,
            @RequestParam(required = false) Integer maxSongs,
            @RequestParam(required = false) String style,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String ids,
            @RequestParam(defaultValue = "alphabetical") String sort,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit) {
        try {
            List<Long> idList = null;
            if (ids != null && !ids.isBlank()) {
                idList = new java.util.ArrayList<>();
                for (String part : ids.split(",")) {
                    try {
                        idList.add(Long.parseLong(part.trim()));
                    } catch (NumberFormatException ignored) {}
                }
            }

            Map<String, Object> result = artistManageService.listArtists(
                    mode, keyword, letter, minSongs, maxSongs,
                    style, country, idList, sort, offset, limit);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("查询艺术家列表失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "查询失败: " + e.getMessage()));
        }
    }

    /**
     * GET /api/editor/artists/providers — 返回支持艺术家详情查询的标签源列表。
     */
    @GetMapping("/providers")
    public ResponseEntity<?> getProviders() {
        return ResponseEntity.ok(Map.of("providers", artistEnrichPipeline.getProviders()));
    }

    /**
     * GET /api/editor/artists/duplicates — 查找大小写重复的艺术家分组。
     */
    @GetMapping("/duplicates")
    public ResponseEntity<?> getDuplicates() {
        try {
            List<Map<String, Object>> dups = artistManageService.findDuplicates();
            return ResponseEntity.ok(Map.of("duplicates", dups));
        } catch (Exception e) {
            log.error("查询重复艺术家失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "查询失败: " + e.getMessage()));
        }
    }

    /**
     * POST /api/editor/artists/merge — 提交艺术家合并任务。
     */
    @PostMapping("/merge")
    public ResponseEntity<?> submitMerge(@RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> groups = (List<Map<String, Object>>) body.get("groups");
            if (groups == null || groups.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "groups 不能为空"));
            }

            List<MergeGroup> mergeGroups = new java.util.ArrayList<>();
            for (Map<String, Object> g : groups) {
                MergeGroup mg = new MergeGroup();
                mg.setLabel((String) g.get("label"));
                @SuppressWarnings("unchecked")
                List<?> sIdsRaw = (List<?>) g.get("sourceIds");
                if (sIdsRaw != null) {
                    mg.setSourceIds(sIdsRaw.stream()
                            .map(o -> o instanceof Number n ? n.longValue() : Long.parseLong(o.toString()))
                            .toList());
                }
                Object tidRaw = g.get("targetId");
                if (tidRaw instanceof Number n) mg.setTargetId(n.longValue());
                else if (tidRaw instanceof String s) mg.setTargetId(Long.parseLong(s));
                mergeGroups.add(mg);
            }

            String pipelineId = pipelineService.submitDataTemplate("artist-merge",
                    Map.of("merge.groups", mergeGroups));

            log.info("艺术家合并已提交: groups={}, pipelineId={}", mergeGroups.size(), pipelineId);
            return ResponseEntity.ok(Map.of(
                    "pipelineId", pipelineId,
                    "template", "artist-merge",
                    "groups", mergeGroups.size(),
                    "state", "RUNNING"
            ));
        } catch (Exception e) {
            log.error("提交艺术家合并失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "提交失败: " + e.getMessage()));
        }
    }

    /**
     * POST /api/editor/artists/pipeline — 提交艺术家 Pipeline 任务（enrich / normalize 等）。
     */
    @PostMapping("/pipeline")
    public ResponseEntity<?> submitArtistPipeline(@RequestBody Map<String, Object> body) {
        try {
            String template = (String) body.get("template");
            if (template == null || template.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "template 不能为空"));
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> selection = (Map<String, Object>) body.get("selection");

            Map<String, Object> data = new HashMap<>();
            data.put("artist.selection", selection != null ? selection : Map.of());

            Map<String, Object> moduleOpts = buildModuleOptions(template, selection);
            log.info("submitArtistPipeline: template={}, moduleOpts={}", template, moduleOpts);
            if (!moduleOpts.isEmpty()) {
                data.put("options", Map.of(template, moduleOpts));
            }

            String pipelineId = pipelineService.submitDataTemplate(template, data);

            log.info("艺术家 Pipeline 已提交: template={}, pipelineId={}", template, pipelineId);
            return ResponseEntity.ok(Map.of(
                    "pipelineId", pipelineId,
                    "template", template,
                    "state", "RUNNING"
            ));
        } catch (Exception e) {
            log.error("提交艺术家管道失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "提交失败: " + e.getMessage()));
        }
    }

    /**
     * POST /api/editor/artists/{id}/search — 单艺术家多源查询预览。
     *
     * <p>对所有启用的 Provider 发起查询，返回全部结果（按评分降序），
     * 同时返回 DB 现有值，供前端对比选择。
     *
     * 请求体（可选）：
     * <pre>
     * { "providers": ["qqmusic", "netease"] }
     * </pre>
     */
    @PostMapping("/{id}/search")
    public ResponseEntity<?> searchArtist(@PathVariable Long id,
                                          @RequestBody(required = false) Map<String, Object> body) {
        try {
            Artist artist = artistManageService.findArtistById(id);
            if (artist == null) {
                return ResponseEntity.notFound().build();
            }

            String providerName = null;
            if (body != null) {
                Object p = body.get("providers");
                if (p instanceof List<?> list && !list.isEmpty()) {
                    providerName = list.stream().map(Object::toString)
                            .collect(Collectors.joining(","));
                } else if (p instanceof String s && !s.isBlank()) {
                    providerName = s;
                }
            }

            // 查询所有 Provider
            List<ArtistEnrichPipeline.ScoredResult> results =
                    artistEnrichPipeline.searchAll(artist.getArtistName(), providerName);

            // 当前 DB 值
            Map<String, Object> current = new LinkedHashMap<>();
            current.put("introduction", artist.getIntroduction());
            current.put("gender", artist.getGender());
            current.put("country", artist.getCountry());
            current.put("artistCover", artist.getArtistCover());
            current.put("enrichSource", artist.getEnrichSource());

            // 组装响应
            List<Map<String, Object>> resultList = new ArrayList<>();
            for (var sr : results) {
                ArtistInfo info = sr.info();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("source", info.source());
                item.put("score", sr.score());
                item.put("artistName", info.artistName());
                item.put("introduction", info.introduction());
                item.put("gender", info.gender());
                item.put("country", info.country());
                item.put("coverUrl", info.coverUrl());
                resultList.add(item);
            }

            return ResponseEntity.ok(Map.of(
                    "artistId", id,
                    "artistName", artist.getArtistName(),
                    "current", current,
                    "results", resultList
            ));
        } catch (Exception e) {
            log.error("单艺术家查询失败: id={}", id, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "查询失败: " + e.getMessage()));
        }
    }

    /**
     * POST /api/editor/artists/{id}/apply — 应用选定的增强结果到 DB。
     *
     * <p>重新查询指定 Provider 获取最新数据，按指定模式写入 DB。
     *
     * 请求体：
     * <pre>
     * { "source": "qqmusic", "mode": "overwrite" }
     * </pre>
     *
     * 也可传 fields 手动指定字段值（覆盖 Provider 返回值）：
     * <pre>
     * { "source": "qqmusic", "mode": "fill", "fields": { "country": "中国" } }
     * </pre>
     */
    @PostMapping("/{id}/apply")
    public ResponseEntity<?> applyArtist(@PathVariable Long id,
                                         @RequestBody Map<String, Object> body) {
        try {
            Artist artist = artistManageService.findArtistById(id);
            if (artist == null) {
                return ResponseEntity.notFound().build();
            }

            // 解析 source
            String source = (String) body.get("source");
            if (source == null || source.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "source 不能为空"));
            }

            // 解析 mode
            String modeStr = (String) body.getOrDefault("mode", "fill");
            ArtistEnrichWriter.WriteMode mode = "overwrite".equalsIgnoreCase(modeStr)
                    ? ArtistEnrichWriter.WriteMode.OVERWRITE
                    : ArtistEnrichWriter.WriteMode.FILL;

            // 查询指定 Provider
            ArtistInfo info = artistEnrichPipeline.search(artist.getArtistName(), source);

            // 允许前端手动覆盖部分字段
            @SuppressWarnings("unchecked")
            Map<String, Object> overrides = (Map<String, Object>) body.get("fields");
            if (overrides != null && !overrides.isEmpty()) {
                info = applyFieldOverrides(info, overrides);
            }

            if (!info.hasData()) {
                // 即使无数据也更新 enrichSource
                artistManageService.updateArtist(id, artist.getArtistName(),
                        artist.getIntroduction(), artist.getGender(),
                        artist.getCountry(), artist.getArtistCover(), source);
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "artistId", id,
                        "artistName", artist.getArtistName(),
                        "source", source,
                        "mode", modeStr,
                        "fieldsWritten", List.of("enrichSource")
                ));
            }

            // 委托 Writer 写入
            ArtistEnrichWriter.ApplyResult result = artistEnrichWriter.apply(artist, info, mode);

            log.info("applyArtist: id={} name=[{}] source={} mode={} → {}",
                    id, artist.getArtistName(), source, mode, result.summary());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "artistId", id,
                    "artistName", artist.getArtistName(),
                    "source", source,
                    "mode", modeStr,
                    "fieldsWritten", result.fields()
            ));
        } catch (Exception e) {
            log.error("应用艺术家增强失败: id={}", id, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "应用失败: " + e.getMessage()));
        }
    }

    /** 用前端传入的字段覆盖 ArtistInfo */
    private static ArtistInfo applyFieldOverrides(ArtistInfo info, Map<String, Object> overrides) {
        String introduction = getStringOverride(overrides, "introduction", info.introduction());
        Integer gender = getIntOverride(overrides, "gender", info.gender());
        String country = getStringOverride(overrides, "country", info.country());
        String coverUrl = getStringOverride(overrides, "coverUrl", info.coverUrl());
        return new ArtistInfo(info.artistName(), introduction, gender, country, coverUrl, info.source());
    }

    private static String getStringOverride(Map<String, Object> map, String key, String fallback) {
        Object v = map.get(key);
        return (v instanceof String s && !s.isBlank()) ? s : fallback;
    }

    private static Integer getIntOverride(Map<String, Object> map, String key, Integer fallback) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        return fallback;
    }

    /**
     * 从 selection 中提取 per-module 配置，按标准 options 格式返回。
     */
    private Map<String, Object> buildModuleOptions(String template, Map<String, Object> selection) {
        if (selection == null) return Map.of();
        return switch (template) {
            case "artist-enrich" -> {
                Map<String, Object> opts = new HashMap<>();

                Object providers = selection.get("providers");
                if (providers instanceof List<?> list && !list.isEmpty()) {
                    opts.put("provider",
                            list.stream().map(Object::toString).collect(Collectors.joining(",")));
                } else if (providers instanceof String s && !s.isBlank()) {
                    opts.put("provider", s);
                }

                Object writeMode = selection.get("writeMode");
                if (writeMode instanceof String s && !s.isBlank()) {
                    opts.put("writeMode", s);
                }

                yield opts.isEmpty() ? Map.of() : opts;
            }
            default -> Map.of();
        };
    }
}
