package com.gjl.music.controller.song;

import com.gjl.music.dto.ToolRequest;
import com.gjl.music.service.song.ToolService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 工具 REST API —— 异步提交管道，返回 pipelineId，前端通过 WebSocket 跟踪进度。
 *
 * <p>端点：
 * <ul>
 *   <li>POST /api/tools/repair  — 乱码修复</li>
 *   <li>POST /api/tools/convert — 繁简转换</li>
 *   <li>POST /api/tools/e.nrich  — 元数据增强</li>
 *   <li>POST /api/tools/dedup   — 重复检测</li>
 *   <li>POST /api/tools/write   — 标签写回</li>
 *   <li>POST /api/tools/import-db — 导入数据库</li>
 *   <li>POST /api/tools/split  — 拆分元数据</li>
 *   <li>POST /api/tools/replace — 替换文本</li>
 *   <li>POST /api/tools/format-convert — 格式转换</li>
 *   <li>POST /api/tools/cue-split — 音轨分割</li>
 *   <li>POST /api/tools/delete — 删除文件/文件夹</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/tools")
@PreAuthorize("hasAuthority('music:write')")
public class ToolController {

    private final ToolService toolService;

    public ToolController(ToolService toolService) {
        this.toolService = toolService;
    }

    @PostMapping("/repair")
    public ResponseEntity<?> repair(@RequestBody ToolRequest request) {
        return submit(() -> toolService.repairEncoding(request.getOptions()));
    }

    @PostMapping("/convert")
    public ResponseEntity<?> convert(@RequestBody ToolRequest request) {
        return submit(() -> toolService.convertChinese(request.getOptions()));
    }

    @PostMapping("/enrich")
    public ResponseEntity<?> enrich(@RequestBody ToolRequest request) {
        return submit(() -> toolService.enrichMetadata(request.getOptions()));
    }

    @PostMapping("/dedup")
    public ResponseEntity<?> dedup(@RequestBody ToolRequest request) {
        return submit(() -> toolService.detectDuplicates(request.getOptions()));
    }

    @PostMapping("/write")
    public ResponseEntity<?> write(@RequestBody ToolRequest request) {
        return submit(() -> toolService.writeTags(request.getOptions()));
    }

    @PostMapping("/import-db")
    public ResponseEntity<?> importToDatabase(@RequestBody ToolRequest request) {
        return submit(() -> toolService.importToDatabase(request.getOptions()));
    }

    @PostMapping("/split")
    public ResponseEntity<?> split(@RequestBody ToolRequest request) {
        return submit(() -> toolService.executeSplit(request.getOptions()));
    }

    @PostMapping("/replace")
    public ResponseEntity<?> replace(@RequestBody ToolRequest request) {
        return submit(() -> toolService.executeReplace(request.getOptions()));
    }

    @PostMapping("/format-convert")
    public ResponseEntity<?> formatConvert(@RequestBody ToolRequest request) {
        return submit(() -> toolService.formatConvert(request.getOptions()));
    }

    @PostMapping("/cue-split")
    public ResponseEntity<?> cueSplit(@RequestBody ToolRequest request) {
        return submit(() -> toolService.cueSplit(request.getOptions()));
    }

    @PostMapping("/organize")
    public ResponseEntity<?> organize(@RequestBody ToolRequest request) {
        return submit(() -> toolService.organizeFiles(request.getOptions()));
    }

    @PostMapping("/delete")
    public ResponseEntity<?> delete(@RequestBody ToolRequest request) {
        return submit(() -> toolService.deleteFiles(request.getOptions()));
    }

    private ResponseEntity<?> submit(ToolSupplier supplier) {
        try {
            String pipelineId = supplier.get();
            return ResponseEntity.ok(Map.of(
                    "pipelineId", pipelineId,
                    "state", "RUNNING"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("工具提交失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "工具提交失败: " + e.getMessage()));
        }
    }

    @FunctionalInterface
    private interface ToolSupplier {
        String get() throws Exception;
    }
}
