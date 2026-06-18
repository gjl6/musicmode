package com.gjl.music.controller;

import com.gjl.music.dto.ToolRequest;
import com.gjl.music.service.ToolService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@Slf4j
@RestController
@RequestMapping("/api/tools")
@PreAuthorize("hasAuthority('music:edit')")
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
                    "submitted", true,
                    "success", true
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
