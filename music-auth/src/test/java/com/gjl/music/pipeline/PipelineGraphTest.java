package com.gjl.music.pipeline;

import com.gjl.music.pipeline.graph.GraphNode;
import com.gjl.music.pipeline.graph.PipelineGraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


@DisplayName("PipelineGraph 图结构测试")
class PipelineGraphTest {


    @Nested
    @DisplayName("基础拓扑构建")
    class BasicConstruction {

        @Test
        @DisplayName("单节点图")
        void singleNode() {
            GraphNode n = GraphNode.builder("filesystem").moduleName("filesystem").finalNode(true).build();
            PipelineGraph g = new PipelineGraph(List.of(n));

            assertEquals(1, g.size());
            assertEquals("filesystem", g.getFinalNodeId());
            assertEquals(1, g.getDepthGroups().size());
            assertEquals(1, g.getDepthGroups().get(0).size());
        }

        @Test
        @DisplayName("两节点线性: filesystem → parser")
        void twoNodeLinear() {
            GraphNode parser = GraphNode.builder("parser").moduleName("parser")
                    .dependsOn("filesystem").finalNode(true).build();
            GraphNode fs = GraphNode.builder("filesystem").moduleName("filesystem").build();
            PipelineGraph g = new PipelineGraph(List.of(parser, fs));

            assertEquals(2, g.size());
            List<List<GraphNode>> groups = g.getDepthGroups();
            assertEquals(2, groups.size());
                        assertEquals(1, groups.get(0).size());
            assertEquals("filesystem", groups.get(0).get(0).getId());
                        assertEquals(1, groups.get(1).size());
            assertEquals("parser", groups.get(1).get(0).getId());
        }

        @Test
        @DisplayName("fan-out 拓扑: scanner → {db-sync, fingerprint} 同深度并行")
        void fanOutTopology() {
            GraphNode scanner = GraphNode.builder("scanner").moduleName("scanner").build();
            GraphNode dbSync = GraphNode.builder("db-sync").moduleName("db-sync")
                    .dependsOn("scanner").build();
            GraphNode fingerprint = GraphNode.builder("fingerprint").moduleName("fingerprint")
                    .dependsOn("scanner").finalNode(true).build();
            PipelineGraph g = new PipelineGraph(List.of(scanner, dbSync, fingerprint));

            List<List<GraphNode>> groups = g.getDepthGroups();
            assertEquals(2, groups.size());
                        assertEquals(List.of("scanner"), nodeIds(groups.get(0)));
                        assertEquals(2, groups.get(1).size());
            assertTrue(nodeIds(groups.get(1)).containsAll(List.of("db-sync", "fingerprint")));
        }

        @Test
        @DisplayName("fan-in 拓扑: {A, B} → C")
        void fanInTopology() {
            GraphNode a = GraphNode.builder("a").moduleName("a").build();
            GraphNode b = GraphNode.builder("b").moduleName("b").build();
            GraphNode c = GraphNode.builder("c").moduleName("c")
                    .dependsOn("a", "b").finalNode(true).build();
            PipelineGraph g = new PipelineGraph(List.of(a, b, c));

            List<List<GraphNode>> groups = g.getDepthGroups();
            assertEquals(2, groups.size());
                        assertEquals(2, groups.get(0).size());
                        assertEquals(1, groups.get(1).size());
            assertEquals("c", groups.get(1).get(0).getId());
        }

        @Test
        @DisplayName("三层混合: scanner → {db-sync, fingerprint} → writer")
        void threeLayerMixed() {
            GraphNode scanner = GraphNode.builder("scanner").moduleName("scanner").build();
            GraphNode dbSync = GraphNode.builder("db-sync").moduleName("db-sync")
                    .dependsOn("scanner").build();
            GraphNode fingerprint = GraphNode.builder("fingerprint").moduleName("fingerprint")
                    .dependsOn("scanner").build();
            GraphNode writer = GraphNode.builder("writer").moduleName("writer")
                    .dependsOn("db-sync", "fingerprint").finalNode(true).build();
            PipelineGraph g = new PipelineGraph(List.of(scanner, dbSync, fingerprint, writer));

            List<List<GraphNode>> groups = g.getDepthGroups();
            assertEquals(3, groups.size());
            assertEquals(List.of("scanner"), nodeIds(groups.get(0)));
            assertEquals(2, groups.get(1).size());
            assertEquals(List.of("writer"), nodeIds(groups.get(2)));
        }

        @Test
        @DisplayName("linear 工厂方法生成正确结构")
        void linearFactory() {
            PipelineGraph g = PipelineGraph.linear("filesystem", "parser", "db-operator", "db-sync");

            assertEquals(4, g.size());
            assertEquals("db-sync", g.getFinalNodeId());
            List<List<GraphNode>> groups = g.getDepthGroups();
            assertEquals(4, groups.size());
            for (int i = 0; i < 4; i++) {
                assertEquals(1, groups.get(i).size());
            }
        }
    }


    @Nested
    @DisplayName("图校验")
    class Validation {

        @Test
        @DisplayName("无 finalNode 抛异常")
        void noFinalNodeThrows() {
            GraphNode a = GraphNode.builder("a").moduleName("a").build();
            GraphNode b = GraphNode.builder("b").moduleName("b").dependsOn("a").build();

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> new PipelineGraph(List.of(a, b)));
            assertTrue(ex.getMessage().contains("finalNode"));
        }

        @Test
        @DisplayName("多个 finalNode 抛异常")
        void multipleFinalNodesThrows() {
            GraphNode a = GraphNode.builder("a").moduleName("a").finalNode(true).build();
            GraphNode b = GraphNode.builder("b").moduleName("b").finalNode(true).build();

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> new PipelineGraph(List.of(a, b)));
            assertTrue(ex.getMessage().contains("finalNode"));
        }

        @Test
        @DisplayName("重复节点 ID 抛异常")
        void duplicateIdThrows() {
            GraphNode a1 = GraphNode.builder("dup").moduleName("a1").finalNode(true).build();
            GraphNode a2 = GraphNode.builder("dup").moduleName("a2").finalNode(true).build();

            assertThrows(IllegalArgumentException.class,
                    () -> new PipelineGraph(List.of(a1, a2)));
        }

        @Test
        @DisplayName("依赖不存在的节点抛异常")
        void unknownDependencyThrows() {
            GraphNode a = GraphNode.builder("a").moduleName("a")
                    .dependsOn("ghost").finalNode(true).build();

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> new PipelineGraph(List.of(a)));
            assertTrue(ex.getMessage().contains("ghost"));
        }

        @Test
        @DisplayName("自环抛异常")
        void selfLoopThrows() {
            GraphNode a = GraphNode.builder("a").moduleName("a")
                    .dependsOn("a").finalNode(true).build();

            assertThrows(IllegalArgumentException.class,
                    () -> new PipelineGraph(List.of(a)));
        }

        @Test
        @DisplayName("简单环 A→B→A 抛异常")
        void simpleCycleThrows() {
            GraphNode a = GraphNode.builder("a").moduleName("a")
                    .dependsOn("b").finalNode(true).build();
            GraphNode b = GraphNode.builder("b").moduleName("b")
                    .dependsOn("a").build();

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> new PipelineGraph(List.of(a, b)));
            assertTrue(ex.getMessage().contains("cycle"));
        }

        @Test
        @DisplayName("间接环 A→B→C→A 抛异常")
        void indirectCycleThrows() {
            GraphNode a = GraphNode.builder("a").moduleName("a")
                    .dependsOn("c").finalNode(true).build();
            GraphNode b = GraphNode.builder("b").moduleName("b")
                    .dependsOn("a").build();
            GraphNode c = GraphNode.builder("c").moduleName("c")
                    .dependsOn("b").build();

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> new PipelineGraph(List.of(a, b, c)));
            assertTrue(ex.getMessage().contains("cycle"));
        }
    }


    @Nested
    @DisplayName("深度分组")
    class DepthGroups {

        @Test
        @DisplayName("两个独立源节点同深度并行")
        void twoIndependentSources() {
            GraphNode a = GraphNode.builder("a").moduleName("a").build();
            GraphNode b = GraphNode.builder("b").moduleName("b").build();
            GraphNode c = GraphNode.builder("c").moduleName("c")
                    .dependsOn("a", "b").finalNode(true).build();
            PipelineGraph g = new PipelineGraph(List.of(a, b, c));

            List<List<GraphNode>> groups = g.getDepthGroups();
            assertEquals(2, groups.size());
            assertEquals(2, groups.get(0).size());
            assertEquals(1, groups.get(1).size());
        }

        @Test
        @DisplayName("深度取决于最长依赖路径")
        void depthUsesLongestPath() {
                                                GraphNode scanner = GraphNode.builder("scanner").moduleName("scanner").build();
            GraphNode dbSync = GraphNode.builder("db-sync").moduleName("db-sync")
                    .dependsOn("scanner").build();
            GraphNode fp = GraphNode.builder("fingerprint").moduleName("fingerprint")
                    .dependsOn("scanner").build();
            GraphNode writer = GraphNode.builder("writer").moduleName("writer")
                    .dependsOn("fingerprint", "db-sync").finalNode(true).build();
            PipelineGraph g = new PipelineGraph(List.of(scanner, dbSync, fp, writer));

            List<List<GraphNode>> groups = g.getDepthGroups();
            assertEquals(3, groups.size());
            assertEquals(List.of("scanner"), nodeIds(groups.get(0)));
                        assertEquals(2, groups.get(1).size());
            assertEquals(List.of("writer"), nodeIds(groups.get(2)));
        }
    }


    private static List<String> nodeIds(List<GraphNode> nodes) {
        return nodes.stream().map(GraphNode::getId).toList();
    }
}
