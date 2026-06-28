package com.gjl.music.config;

import com.gjl.music.module.Module;
import com.gjl.music.infra.pipeline.NodeHandler;
import com.gjl.music.infra.pipeline.PipelineFactory;
import com.gjl.music.infra.pipeline.template.TopologyBuilder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.*;

/**
 * 管道模块注册配置 —— 注册所有模板拓扑 + NodeHandler 到 PipelineFactory。
 *
 * <p>模块如同时实现 Module + NodeHandler，直接注册为 handler。
 */
@Slf4j
@Configuration
public class PipelineModuleConfig {

    private final PipelineFactory pipelineFactory;
    private final List<Module> modules;

    public PipelineModuleConfig(PipelineFactory pipelineFactory,
                                 List<Module> modules) {
        this.pipelineFactory = pipelineFactory;
        this.modules = modules;
    }

    @PostConstruct
    public void init() {
        registerHandlers();
        registerTemplates();
        log.info("Pipeline initialized: {} handlers, {} templates",
                pipelineFactory.moduleNames().size(), pipelineFactory.templateNames().size());
    }

    /** 注册实现 NodeHandler 的模块 */
    private void registerHandlers() {
        for (Module module : modules) {
            if (module instanceof NodeHandler handler) {
                pipelineFactory.registerHandler(module.name(), handler);
                log.debug("Registered handler: {}", module.name());
            }
        }
    }

    /** 注册所有管道模板 */
    private void registerTemplates() {
        pipelineFactory.registerTemplate("browse",
                TopologyBuilder.sequential("filesystem"));

        pipelineFactory.registerTemplate("browse-parse",
                TopologyBuilder.sequential("scanner", "parser", "db-operator", "db-sync"));

        pipelineFactory.registerTemplate("import-collection",
                TopologyBuilder.sequential("scanner", "import-to-db"));

        pipelineFactory.registerTemplate("parse",
                TopologyBuilder.sequential("filesystem", "parser", "db-operator", "db-sync"),
                ctx -> ctx.setSlot("singleFile", true));

        pipelineFactory.registerTemplate("write",
                TopologyBuilder.fanOut("scanner",
                        List.of("db-sync"),
                        List.of("batch-write")));

        pipelineFactory.registerTemplate("repair",
                TopologyBuilder.fanOut("scanner",
                        List.of("db-sync"),
                        List.of("encoding-repair")));

        pipelineFactory.registerTemplate("convert",
                TopologyBuilder.fanOut("scanner",
                        List.of("db-sync"),
                        List.of("chinese-convert")));

        pipelineFactory.registerTemplate("enrich",
                TopologyBuilder.fanOut("scanner",
                        List.of("db-sync"),
                        List.of("enrich")));

        pipelineFactory.registerTemplate("fingerprint",
                TopologyBuilder.fanOut("scanner",
                        List.of("db-sync"),
                        List.of("fingerprint")));

        pipelineFactory.registerTemplate("scan-fingerprint",
                TopologyBuilder.fanOut("scanner",
                        List.of("db-sync"),
                        List.of("fingerprint")));

        pipelineFactory.registerTemplate("dedup",
                TopologyBuilder.fanOut("scanner",
                        List.of("db-sync"),
                        List.of("dedup")));

        pipelineFactory.registerTemplate("split",
                TopologyBuilder.fanOut("scanner",
                        List.of("db-sync"),
                        List.of("split-metadata")));

        pipelineFactory.registerTemplate("replace",
                TopologyBuilder.fanOut("scanner",
                        List.of("db-sync"),
                        List.of("replace-text")));

        pipelineFactory.registerTemplate("format-convert",
                TopologyBuilder.fanOut("scanner",
                        List.of("db-sync"),
                        List.of("format-convert")));

        pipelineFactory.registerTemplate("cue-split",
                TopologyBuilder.fanOut("scanner",
                        List.of("db-sync"),
                        List.of("cue-split")));

        pipelineFactory.registerTemplate("organize",
                TopologyBuilder.fanOut("scanner",
                        List.of("db-sync"),
                        List.of("organize")));

        pipelineFactory.registerTemplate("artist-merge",
                TopologyBuilder.sequential("artist-merge"));

        pipelineFactory.registerTemplate("artist-enrich",
                TopologyBuilder.fanOut("artist-scanner", List.of(), List.of("artist-enrich")));

        pipelineFactory.registerTemplate("artist-normalize",
                TopologyBuilder.fanOut("artist-scanner", List.of(), List.of("artist-normalize")));

        pipelineFactory.registerTemplate("album-merge",
                TopologyBuilder.sequential("album-merge"));

        pipelineFactory.registerTemplate("album-enrich",
                TopologyBuilder.fanOut("album-scanner", List.of(), List.of("album-enrich")));

        pipelineFactory.registerTemplate("album-normalize",
                TopologyBuilder.fanOut("album-scanner", List.of(), List.of("album-normalize")));

        pipelineFactory.registerTemplate("delete",
                TopologyBuilder.sequential("delete"));
    }
}
