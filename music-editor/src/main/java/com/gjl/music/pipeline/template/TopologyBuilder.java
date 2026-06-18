package com.gjl.music.pipeline.template;

import com.gjl.music.pipeline.graph.GraphNode;
import com.gjl.music.pipeline.graph.PipelineGraph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public final class TopologyBuilder {

    private TopologyBuilder() {}


    public static PipelineGraph sequential(String... ids) {
        return PipelineGraph.linear(ids);
    }


    public static PipelineGraph fanOut(String source,
                                        List<String> passiveBranches,
                                        List<String> activeBranches) {
        if (activeBranches == null || activeBranches.isEmpty()) {
            throw new IllegalArgumentException("fanOut requires at least one active branch");
        }

        List<GraphNode> nodes = new ArrayList<>();

                nodes.add(GraphNode.builder(source).moduleName(source).build());

                for (String id : passiveBranches) {
            nodes.add(GraphNode.builder(id).moduleName(id).dependsOn(source).build());
        }

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


    public static PipelineGraph mixed(List<GraphNode> nodes) {
        return new PipelineGraph(nodes);
    }


    public static PipelineGraph mixed(GraphNode... nodes) {
        return new PipelineGraph(Arrays.asList(nodes));
    }
}
