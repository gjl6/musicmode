package com.gjl.music.controller.album;

import com.gjl.music.model.Album;
import com.gjl.music.module.album.enrich.AlbumEnrichPipeline;
import com.gjl.music.module.album.enrich.AlbumEnrichWriter;
import com.gjl.music.module.album.albummerge.AlbumMergeGroup;
import com.gjl.music.module.album.enrich.provider.AlbumInfo;
import com.gjl.music.service.album.AlbumManageService;
import com.gjl.music.service.song.PipelineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 涓撹緫绠＄悊 REST API 鈥?鍒楄〃/鎼滅储 + 閲嶅妫€娴?+ 鍚堝苟鎻愪氦 + 鍗曚笓杈戝寮洪瑙?搴旂敤銆? */
@Slf4j
@RestController
@RequestMapping("/api/editor/albums")
@PreAuthorize("hasAuthority('album:write')")
public class AlbumManageController {

    private final AlbumManageService albumManageService;
    private final PipelineService pipelineService;
    private final AlbumEnrichPipeline albumEnrichPipeline;
    private final AlbumEnrichWriter albumEnrichWriter;

    public AlbumManageController(AlbumManageService albumManageService,
                                  PipelineService pipelineService,
                                  AlbumEnrichPipeline albumEnrichPipeline,
                                  AlbumEnrichWriter albumEnrichWriter) {
        this.albumManageService = albumManageService;
        this.pipelineService = pipelineService;
        this.albumEnrichPipeline = albumEnrichPipeline;
        this.albumEnrichWriter = albumEnrichWriter;
    }

    /**
     * GET /api/editor/albums 鈥?鍒嗛〉鎼滅储涓撹緫鍒楄〃锛堟敮鎸?mode 閫夋嫨鍜屽彔鍔犺繃婊わ級銆?     */
    @GetMapping
    public ResponseEntity<?> listAlbums(
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String letter,
            @RequestParam(required = false) Long artistId,
            @RequestParam(required = false) Integer minSongs,
            @RequestParam(required = false) Integer maxSongs,
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

            Map<String, Object> result = albumManageService.listAlbums(
                    mode, keyword, letter, artistId, minSongs, maxSongs,
                    idList, sort, offset, limit);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("鏌ヨ涓撹緫鍒楄〃澶辫触", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "鏌ヨ澶辫触: " + e.getMessage()));
        }
    }

    /**
     * GET /api/editor/albums/providers 鈥?杩斿洖鏀寔涓撹緫璇︽儏鏌ヨ鐨勬爣绛炬簮鍒楄〃銆?     */
    @GetMapping("/providers")
    public ResponseEntity<?> getProviders() {
        return ResponseEntity.ok(Map.of("providers", albumEnrichPipeline.getProviders()));
    }

    /**
     * GET /api/editor/albums/duplicates 鈥?鏌ユ壘澶у皬鍐欓噸澶嶇殑涓撹緫鍒嗙粍銆?     */
    @GetMapping("/duplicates")
    public ResponseEntity<?> getDuplicates() {
        try {
            List<Map<String, Object>> dups = albumManageService.findDuplicates();
            return ResponseEntity.ok(Map.of("duplicates", dups));
        } catch (Exception e) {
            log.error("鏌ヨ閲嶅涓撹緫澶辫触", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "鏌ヨ澶辫触: " + e.getMessage()));
        }
    }

    /**
     * POST /api/editor/albums/merge 鈥?鎻愪氦涓撹緫鍚堝苟浠诲姟銆?     */
    @PostMapping("/merge")
    public ResponseEntity<?> submitMerge(@RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> groups = (List<Map<String, Object>>) body.get("groups");
            if (groups == null || groups.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "groups 涓嶈兘涓虹┖"));
            }

            List<AlbumMergeGroup> mergeGroups = new java.util.ArrayList<>();
            for (Map<String, Object> g : groups) {
                AlbumMergeGroup mg = new AlbumMergeGroup();
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

            String pipelineId = pipelineService.submitDataTemplate("album-merge",
                    Map.of("merge.groups", mergeGroups));

            log.info("涓撹緫鍚堝苟宸叉彁浜? groups={}, pipelineId={}", mergeGroups.size(), pipelineId);
            return ResponseEntity.ok(Map.of(
                    "pipelineId", pipelineId,
                    "template", "album-merge",
                    "groups", mergeGroups.size(),
                    "state", "RUNNING"
            ));
        } catch (Exception e) {
            log.error("鎻愪氦涓撹緫鍚堝苟澶辫触", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "鎻愪氦澶辫触: " + e.getMessage()));
        }
    }

    /**
     * POST /api/editor/albums/pipeline 鈥?鎻愪氦涓撹緫 Pipeline 浠诲姟锛坋nrich / normalize 绛夛級銆?     */
    @PostMapping("/pipeline")
    public ResponseEntity<?> submitAlbumPipeline(@RequestBody Map<String, Object> body) {
        try {
            String template = (String) body.get("template");
            if (template == null || template.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "template 涓嶈兘涓虹┖"));
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> selection = (Map<String, Object>) body.get("selection");

            Map<String, Object> data = new HashMap<>();
            data.put("album.selection", selection != null ? selection : Map.of());

            Map<String, Object> moduleOpts = buildModuleOptions(template, selection);
            log.info("submitAlbumPipeline: template={}, moduleOpts={}", template, moduleOpts);
            if (!moduleOpts.isEmpty()) {
                data.put("options", Map.of(template, moduleOpts));
            }

            String pipelineId = pipelineService.submitDataTemplate(template, data);

            log.info("涓撹緫 Pipeline 宸叉彁浜? template={}, pipelineId={}", template, pipelineId);
            return ResponseEntity.ok(Map.of(
                    "pipelineId", pipelineId,
                    "template", template,
                    "state", "RUNNING"
            ));
        } catch (Exception e) {
            log.error("鎻愪氦涓撹緫绠￠亾澶辫触", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "鎻愪氦澶辫触: " + e.getMessage()));
        }
    }

    /**
     * POST /api/editor/albums/{id}/search 鈥?鍗曚笓杈戝婧愭煡璇㈤瑙堛€?     *
     * <p>瀵规墍鏈夊惎鐢ㄧ殑 Provider 鍙戣捣鏌ヨ锛岃繑鍥炲叏閮ㄧ粨鏋滐紙鎸夎瘎鍒嗛檷搴忥級锛?     * 鍚屾椂杩斿洖 DB 鐜版湁鍊硷紝渚涘墠绔姣旈€夋嫨銆?     */
    @PostMapping("/{id}/search")
    public ResponseEntity<?> searchAlbum(@PathVariable Long id,
                                          @RequestBody(required = false) Map<String, Object> body) {
        try {
            Album album = albumManageService.findAlbumById(id);
            if (album == null) {
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

            // Resolve artist name for better search accuracy
            String artistName = albumManageService.resolveArtistName(album.getArtistId());

            // 鏌ヨ鎵€鏈?Provider
            List<AlbumEnrichPipeline.ScoredResult> results =
                    albumEnrichPipeline.searchAll(album.getAlbumName(), artistName, providerName);

            Map<String, Object> current = new LinkedHashMap<>();
            current.put("albumName", album.getAlbumName());
            current.put("albumType", album.getAlbumType() != null ? album.getAlbumType().name() : null);
            current.put("albumYear", album.getAlbumYear());
            current.put("introduction", album.getIntroduction());
            current.put("company", album.getCompany());
            current.put("language", album.getLanguage());
            current.put("albumCover", album.getAlbumCover());
            current.put("enrichSource", album.getEnrichSource());

            // 缁勮鍝嶅簲
            List<Map<String, Object>> resultList = new ArrayList<>();
            for (var sr : results) {
                AlbumInfo info = sr.info();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("source", info.source());
                item.put("score", sr.score());
                item.put("albumName", info.albumName());
                item.put("introduction", info.introduction());
                item.put("albumType", info.albumType());
                item.put("albumYear", info.albumYear());
                item.put("company", info.company());
                item.put("language", info.language());
                item.put("coverUrl", info.coverUrl());
                resultList.add(item);
            }

            return ResponseEntity.ok(Map.of(
                    "albumId", id,
                    "albumName", album.getAlbumName(),
                    "artistName", artistName,
                    "current", current,
                    "results", resultList
            ));
        } catch (Exception e) {
            log.error("鍗曚笓杈戞煡璇㈠け璐? id={}", id, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "鏌ヨ澶辫触: " + e.getMessage()));
        }
    }

    /**
     * POST /api/editor/albums/{id}/apply 鈥?搴旂敤閫夊畾鐨勫寮虹粨鏋滃埌 DB銆?     *
     * <p>閲嶆柊鏌ヨ鎸囧畾 Provider 鑾峰彇鏈€鏂版暟鎹紝鎸夋寚瀹氭ā寮忓啓鍏?DB銆?     */
    @PostMapping("/{id}/apply")
    public ResponseEntity<?> applyAlbum(@PathVariable Long id,
                                         @RequestBody Map<String, Object> body) {
        try {
            Album album = albumManageService.findAlbumById(id);
            if (album == null) {
                return ResponseEntity.notFound().build();
            }

            // 瑙ｆ瀽 source
            String source = (String) body.get("source");
            if (source == null || source.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "source 涓嶈兘涓虹┖"));
            }

            // 瑙ｆ瀽 mode
            String modeStr = (String) body.getOrDefault("mode", "fill");
            AlbumEnrichWriter.WriteMode mode = "overwrite".equalsIgnoreCase(modeStr)
                    ? AlbumEnrichWriter.WriteMode.OVERWRITE
                    : AlbumEnrichWriter.WriteMode.FILL;

            // Resolve artist name for better search accuracy
            String artistName = albumManageService.resolveArtistName(album.getArtistId());

            // 鏌ヨ鎸囧畾 Provider
            AlbumInfo info = albumEnrichPipeline.search(album.getAlbumName(), artistName, source);

            // 鍏佽鍓嶇鎵嬪姩瑕嗙洊閮ㄥ垎瀛楁
            @SuppressWarnings("unchecked")
            Map<String, Object> overrides = (Map<String, Object>) body.get("fields");
            if (overrides != null && !overrides.isEmpty()) {
                info = applyFieldOverrides(info, overrides);
            }

            if (!info.hasData()) {
                albumManageService.updateAlbum(id, album.getAlbumName(),
                        album.getAlbumType() != null ? album.getAlbumType().name() : null,
                        album.getAlbumYear(), album.getIntroduction(),
                        album.getCompany(), album.getLanguage(),
                        album.getAlbumCover(), source);
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "albumId", id,
                        "albumName", album.getAlbumName(),
                        "source", source,
                        "mode", modeStr,
                        "fieldsWritten", List.of("enrichSource")
                ));
            }

            // 濮旀墭 Writer 鍐欏叆
            AlbumEnrichWriter.ApplyResult result = albumEnrichWriter.apply(album, info, mode);

            log.info("applyAlbum: id={} name=[{}] source={} mode={} -> {}",
                    id, album.getAlbumName(), source, mode, result.summary());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "albumId", id,
                    "albumName", album.getAlbumName(),
                    "source", source,
                    "mode", modeStr,
                    "fieldsWritten", result.fields()
            ));
        } catch (Exception e) {
            log.error("搴旂敤涓撹緫澧炲己澶辫触: id={}", id, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "搴旂敤澶辫触: " + e.getMessage()));
        }
    }

    /** Resolve artist name from artist ID (delegates to service) */
    private String resolveArtistName(Integer artistId) {
        return albumManageService.resolveArtistName(artistId);
    }

    /** 鐢ㄥ墠绔紶鍏ョ殑瀛楁瑕嗙洊 AlbumInfo */
    private static AlbumInfo applyFieldOverrides(AlbumInfo info, Map<String, Object> overrides) {
        String introduction = getStringOverride(overrides, "introduction", info.introduction());
        String albumType = getStringOverride(overrides, "albumType", info.albumType());
        Integer albumYear = getIntOverride(overrides, "albumYear", info.albumYear());
        String company = getStringOverride(overrides, "company", info.company());
        String language = getStringOverride(overrides, "language", info.language());
        String coverUrl = getStringOverride(overrides, "coverUrl", info.coverUrl());
        return new AlbumInfo(info.albumName(), introduction, albumType, albumYear, company, language, coverUrl, info.source());
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
     * 浠?selection 涓彁鍙?per-module 閰嶇疆锛屾寜鏍囧噯 options 鏍煎紡杩斿洖銆?     */
    private Map<String, Object> buildModuleOptions(String template, Map<String, Object> selection) {
        if (selection == null) return Map.of();
        return switch (template) {
            case "album-enrich" -> {
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
