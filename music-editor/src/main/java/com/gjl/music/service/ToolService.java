package com.gjl.music.service;

import com.gjl.music.pipeline.PipelineFactory;
import com.gjl.music.pipeline.PipelineOrchestrator;
import com.gjl.music.pipeline.engine.PipelineEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;


@Slf4j
@Service
public class ToolService {

    private final PipelineFactory pipelineFactory;
    private final PipelineOrchestrator orchestrator;

    public ToolService(PipelineFactory pipelineFactory, PipelineOrchestrator orchestrator) {
        this.pipelineFactory = pipelineFactory;
        this.orchestrator = orchestrator;
    }

    public String repairEncoding(Map<String, Object> options) {
        return submitTemplate("repair", options);
    }

    public String convertChinese(Map<String, Object> options) {
        return submitTemplate("convert", options);
    }

    public String enrichMetadata(Map<String, Object> options) {
        return submitTemplate("enrich", options);
    }

    public String detectDuplicates(Map<String, Object> options) {
        return submitTemplate("dedup", options);
    }

    public String writeTags(Map<String, Object> options) {
        return submitTemplate("write", options);
    }

    public String importToDatabase(Map<String, Object> options) {
        return submitTemplate("browse-parse", options);
    }

    public String executeSplit(Map<String, Object> options) {
        return submitTemplate("split", options);
    }

    public String executeReplace(Map<String, Object> options) {
        return submitTemplate("replace", options);
    }

    public String formatConvert(Map<String, Object> options) {
        return submitTemplate("format-convert", options);
    }

    public String cueSplit(Map<String, Object> options) {
        return submitTemplate("cue-split", options);
    }

    public String organizeFiles(Map<String, Object> options) {
        return submitTemplate("organize", options);
    }

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
