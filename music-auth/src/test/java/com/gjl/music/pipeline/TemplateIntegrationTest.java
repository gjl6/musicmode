package com.gjl.music.pipeline;

import com.gjl.music.pipeline.engine.PipelineEngine;
import com.gjl.music.pipeline.graph.PipelineGraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
@DisplayName("模板集成测试")
class TemplateIntegrationTest {

    @Autowired
    private PipelineFactory pipelineFactory;


    @Test
    @DisplayName("PipelineFactory 正确注入")
    void factoryInjected() {
        assertNotNull(pipelineFactory);
    }

    @Test
    @DisplayName("至少注册了 10 个 handler")
    void handlersRegistered() {
        Set<String> names = pipelineFactory.moduleNames();
        assertTrue(names.size() >= 10,
                "应至少注册 10 个模块 handler，实际: " + names);
        System.out.println("已注册 handlers: " + names);
    }

    @Test
    @DisplayName("所有 14 个模板已注册")
    void allTemplatesRegistered() {
        Set<String> templateNames = pipelineFactory.templateNames();
        List<String> expected = List.of(
                "browse", "browse-parse", "parse", "write",
                "repair", "convert", "enrich", "fingerprint",
                "scan-fingerprint", "dedup", "split", "replace"
        );
        for (String name : expected) {
            assertTrue(templateNames.contains(name),
                    "模板 '" + name + "' 应已注册，已注册: " + templateNames);
        }
        System.out.println("已注册模板: " + templateNames);
    }


    @Nested
    @DisplayName("顺序模板")
    class SequentialTemplates {

        @Test
        @DisplayName("browse: filesystem (单节点)")
        void browseTemplate() {
            PipelineEngine engine = pipelineFactory.createFromTemplate(
                    "browse", "test-browse-001", null, null);
            assertNotNull(engine);
            assertEquals("test-browse-001", engine.getPipelineId());

            PipelineGraph graph = pipelineFactory.getTemplateRegistry().getGraph("browse");
            assertEquals(1, graph.size());
            assertEquals("filesystem", graph.getFinalNodeId());
        }

        @Test
        @DisplayName("browse-parse: filesystem → parser → db-operator → db-sync")
        void browseParseTemplate() {
            PipelineGraph graph = pipelineFactory.getTemplateRegistry().getGraph("browse-parse");
            assertEquals(4, graph.size());
            assertEquals("db-sync", graph.getFinalNodeId());

            List<List<com.gjl.music.pipeline.graph.GraphNode>> groups = graph.getDepthGroups();
            assertEquals(4, groups.size(), "4 个节点每个一个深度层");
        }

        @Test
        @DisplayName("parse: 同 browse-parse 但含 singleFile 初始化器")
        void parseTemplate() {
            PipelineGraph graph = pipelineFactory.getTemplateRegistry().getGraph("parse");
            assertEquals(4, graph.size());
            assertEquals("db-sync", graph.getFinalNodeId());
            assertNotNull(pipelineFactory.getTemplateRegistry().getInitializer("parse"),
                    "parse 模板应有初始化器");
        }

        @Test
        @DisplayName("write: 3 节点 fanOut 链（scanner → {db-sync, batch-write}）")
        void writeTemplate() {
            PipelineGraph graph = pipelineFactory.getTemplateRegistry().getGraph("write");
            assertEquals(3, graph.size());
            assertEquals("batch-write", graph.getFinalNodeId());
        }
    }


    @Nested
    @DisplayName("并行模板 (fan-out)")
    class FanOutTemplates {

        @Test
        @DisplayName("repair: scanner → {db-sync, encoding-repair} 同深度并行")
        void repairTemplate() {
            PipelineGraph graph = pipelineFactory.getTemplateRegistry().getGraph("repair");
            assertEquals(3, graph.size());

            List<List<com.gjl.music.pipeline.graph.GraphNode>> groups = graph.getDepthGroups();
            assertEquals(2, groups.size(), "两层深度");
            assertEquals(1, groups.get(0).size(), "深度0: scanner 一个");
            assertEquals(2, groups.get(1).size(), "深度1: db-sync + encoding-repair 并行");

                        assertEquals("encoding-repair", graph.getFinalNodeId());
        }

        @Test
        @DisplayName("convert: scanner → {db-sync, chinese-convert}")
        void convertTemplate() {
            PipelineGraph graph = pipelineFactory.getTemplateRegistry().getGraph("convert");
            assertEquals(3, graph.size());
            assertEquals("chinese-convert", graph.getFinalNodeId());

            List<List<com.gjl.music.pipeline.graph.GraphNode>> groups = graph.getDepthGroups();
            assertEquals(2, groups.size());
        }

        @Test
        @DisplayName("enrich: scanner → {db-sync, enrich}")
        void enrichTemplate() {
            PipelineGraph graph = pipelineFactory.getTemplateRegistry().getGraph("enrich");
            assertEquals(3, graph.size());
            assertEquals("enrich", graph.getFinalNodeId());
        }

        @Test
        @DisplayName("fingerprint: scanner → {db-sync, fingerprint}")
        void fingerprintTemplate() {
            PipelineGraph graph = pipelineFactory.getTemplateRegistry().getGraph("fingerprint");
            assertEquals(3, graph.size());
            assertEquals("fingerprint", graph.getFinalNodeId());
        }

        @Test
        @DisplayName("dedup: scanner → {db-sync, dedup}")
        void dedupTemplate() {
            PipelineGraph graph = pipelineFactory.getTemplateRegistry().getGraph("dedup");
            assertEquals(3, graph.size());
            assertEquals("dedup", graph.getFinalNodeId());
        }

        @Test
        @DisplayName("split: scanner → {db-sync, split-metadata}")
        void splitTemplate() {
            PipelineGraph graph = pipelineFactory.getTemplateRegistry().getGraph("split");
            assertEquals(3, graph.size());
            assertEquals("split-metadata", graph.getFinalNodeId());
        }

        @Test
        @DisplayName("replace: scanner → {db-sync, replace-text}")
        void replaceTemplate() {
            PipelineGraph graph = pipelineFactory.getTemplateRegistry().getGraph("replace");
            assertEquals(3, graph.size());
            assertEquals("replace-text", graph.getFinalNodeId());
        }
    }


    @Nested
    @DisplayName("Engine 创建")
    class EngineCreation {

        @Test
        @DisplayName("所有模板都能成功创建 Engine")
        void allTemplatesCreateEngine() {
            for (String name : pipelineFactory.templateNames()) {
                PipelineEngine engine = pipelineFactory.createFromTemplate(
                        name, "test-" + name + "-001", null, null);
                assertNotNull(engine, "模板 '" + name + "' 应能创建 Engine");
                assertNotNull(engine.getPipelineId());
                assertNotNull(engine.getProgressTracker());
                assertNotNull(engine.getPauseController());
            }
        }
    }
}
