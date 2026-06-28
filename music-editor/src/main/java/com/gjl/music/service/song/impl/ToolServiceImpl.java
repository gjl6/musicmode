package com.gjl.music.service.song.impl;

import com.gjl.music.service.song.ToolService;
import com.gjl.music.infra.pipeline.PipelineFactory;
import com.gjl.music.infra.pipeline.PipelineOrchestrator;
import com.gjl.music.infra.pipeline.engine.PipelineEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class ToolServiceImpl implements ToolService {

    private final PipelineFactory pipelineFactory;
    private final PipelineOrchestrator orchestrator;

    public ToolServiceImpl(PipelineFactory pipelineFactory, PipelineOrchestrator orchestrator) {
        this.pipelineFactory = pipelineFactory;
        this.orchestrator = orchestrator;
    }

    @Override
    public String repairEncoding(Map<String, Object> options) {
        return submitTemplate("repair", options);
    }

    @Override
    public String convertChinese(Map<String, Object> options) {
        return submitTemplate("convert", options);
    }

    @Override
    public String enrichMetadata(Map<String, Object> options) {
        return submitTemplate("enrich", options);
    }

    @Override
    public String detectDuplicates(Map<String, Object> options) {
        return submitTemplate("dedup", options);
    }

    @Override
    public String writeTags(Map<String, Object> options) {
        return submitTemplate("write", options);
    }

    @Override
    public String importToDatabase(Map<String, Object> options) {
        checkFilesRequired(options, "导入收藏需要选择文件");
        return submitTemplate("import-collection", options);
    }

    @SuppressWarnings("unchecked")
    private void checkFilesRequired(Map<String, Object> options, String message) {
        if (options == null || !(options.get("files") instanceof List<?> files) || files.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    @Override
    public String executeSplit(Map<String, Object> options) {
        return submitTemplate("split", options);
    }

    @Override
    public String executeReplace(Map<String, Object> options) {
        return submitTemplate("replace", options);
    }

    @Override
    public String formatConvert(Map<String, Object> options) {
        return submitTemplate("format-convert", options);
    }

    @Override
    public String cueSplit(Map<String, Object> options) {
        return submitTemplate("cue-split", options);
    }

    @Override
    public String organizeFiles(Map<String, Object> options) {
        return submitTemplate("organize", options);
    }

    @Override
    @SuppressWarnings("unchecked")
    public String deleteFiles(Map<String, Object> options) {
        Map<String, Object> opts = (options != null) ? options : new HashMap<>();
        if (!opts.containsKey("files") || ((List<?>) opts.get("files")).isEmpty()) {
            String browsePath = (String) opts.getOrDefault("path", "");
            if (browsePath != null && !browsePath.isEmpty()) {
                opts = new HashMap<>(opts);
                opts.put("files", List.of(browsePath));
            }
        }
        return submitTemplate("delete", opts);
    }

    private String submitTemplate(String template, Map<String, Object> options) {
        String pipelineId = UUID.randomUUID().toString().replace("-", "");
        java.nio.file.Path[] inputPaths = resolveInputPathsFromOptions(options);
        PipelineEngine engine = pipelineFactory.createFromTemplate(template, pipelineId,
                ctx -> {
                    ctx.setSlot("input.paths", inputPaths);
                    if (options != null) {
                        ctx.setSlot("options", options);
                    }
                },
                orchestrator::pushItemLog);
        String graphJson = pipelineFactory.getGraphJson(pipelineId);
        String[] relativePaths = getFileList(options);
        return orchestrator.submit(engine, template, graphJson, relativePaths);
    }

    @SuppressWarnings("unchecked")
    private String[] getFileList(Map<String, Object> options) {
        if (options != null && options.get("files") instanceof List<?> files
                && !files.isEmpty()) {
            return files.stream()
                    .filter(String.class::isInstance)
                    .map(f -> (String) f)
                    .toArray(String[]::new);
        }
        return new String[0];
    }

    @SuppressWarnings("unchecked")
    private java.nio.file.Path[] resolveInputPathsFromOptions(Map<String, Object> options) {
        if (options != null && options.get("files") instanceof List<?> files
                && !files.isEmpty()) {
            return files.stream()
                    .filter(String.class::isInstance)
                    .map(f -> pipelineFactory.resolvePath((String) f))
                    .toArray(java.nio.file.Path[]::new);
        }
        return new java.nio.file.Path[0];
    }
}
