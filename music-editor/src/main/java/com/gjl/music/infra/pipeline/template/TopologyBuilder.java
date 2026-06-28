package com.gjl.music.infra.pipeline.template;

import com.gjl.music.infra.pipeline.graph.GraphNode;
import com.gjl.music.infra.pipeline.graph.PipelineGraph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 拓扑构建器 —— 三种构建方式覆盖所有常见管道形态。
 *
 * <pre>
 * sequential("A", "B", "C")          →  A → B(final) ??? 最后一个是 final
 * fanOut("scanner", ["db-sync"], ["encoding-repair"])
 *                                    →  scanner → {db-sync, encoding-repair(final)}
 * mixed(nodes)                       →  自由组合
 * </pre>
 */
public final class TopologyBuilder {

    private TopologyBuilder() {}

    /**
     * 线性拓扑：按给定顺序串行，最后一个节点自动设为 finalNode。
     */
    public static PipelineGraph sequential(String... ids) {
        return PipelineGraph.linear(ids);
    }

    /**
     * 单源 + 并行分支。
     *
     * @param source           源节点 ID
     * @param passiveBranches  非最终节点列表（如 db-sync）
     * @param activeBranches   至少一个为最终节点（如 encoding-repair）
     */
    public static PipelineGraph fanOut(String source,
                                        List<String> passiveBranches,
                                        List<String> activeBranches) {
        if (activeBranches == null || activeBranches.isEmpty()) {
            throw new IllegalArgumentException("fanOut requires at least one active branch");
        }

        List<GraphNode> nodes = new ArrayList<>();

        // 源节点
        nodes.add(GraphNode.builder(source).moduleName(source).build());

        // 被动分支（非最终节点）
        for (String id : passiveBranches) {
            nodes.add(GraphNode.builder(id).moduleName(id).dependsOn(source).build());
        }

        // 活跃分支（最后一个为 finalNode）
        for (int i = 0; i < activeBranches.size(); i++) {
            String id = activeBranches.get(i);
            GraphNode.Builder b = GraphNode.builder(id).moduleName(id).dependsOn(source);
            if (i == activeBranches.size() - 1) {
                b.finalNode(true);
            }
            nodes.add(b.build());
        }

        return new PipelineGraph(nodes);
    }

    /**
     * 自由组合拓扑 —— 直接传入已构建好的节点列表。
     * 调用者负责设置 finalNode 和依赖关系。
     */
    public static PipelineGraph mixed(List<GraphNode> nodes) {
        return new PipelineGraph(nodes);
    }

    /**
     * 自由组合（变参）。
     */
    public static PipelineGraph mixed(GraphNode... nodes) {
        return new PipelineGraph(Arrays.asList(nodes));
    }
}
