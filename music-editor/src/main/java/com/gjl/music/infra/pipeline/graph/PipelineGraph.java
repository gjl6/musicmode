package com.gjl.music.infra.pipeline.graph;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 管道拓扑定义 —— 统一节点模型下的 DAG。
 *
 * <p>构造时自动：
 * <ul>
 *   <li>Kahn 拓扑排序</li>
 *   <li>DFS 环检测</li>
 *   <li>验证 finalNode 唯一</li>
 *   <li>计算深度分组（同深度节点无依赖，可并行执行）</li>
 * </ul>
 *
 * <p>深度定义：源节点（无依赖）深度 = 0，其他节点深度 = max(依赖节点深度) + 1。
 */
public class PipelineGraph {

    private final Map<String, GraphNode> nodes;
    private final String finalNodeId;
    private final List<List<GraphNode>> depthGroups;  // 按深度分组，index = depth

    public PipelineGraph(Collection<GraphNode> nodeList) {
        // 索引
        Map<String, GraphNode> nodeMap = new LinkedHashMap<>();
        for (GraphNode node : nodeList) {
            if (nodeMap.containsKey(node.getId())) {
                throw new IllegalArgumentException("Duplicate node id: " + node.getId());
            }
            nodeMap.put(node.getId(), node);
        }
        this.nodes = Collections.unmodifiableMap(nodeMap);

        // 验证依赖引用的节点存在、无自环
        for (GraphNode node : nodes.values()) {
            for (String dep : node.getDependencies()) {
                if (!nodes.containsKey(dep)) {
                    throw new IllegalArgumentException(
                            "Node [" + node.getId() + "] depends on unknown node [" + dep + "]");
                }
                if (dep.equals(node.getId())) {
                    throw new IllegalArgumentException("Node [" + node.getId() + "] has a self-loop");
                }
            }
        }

        // 验证 finalNode 唯一
        List<GraphNode> finalNodes = nodes.values().stream()
                .filter(GraphNode::isFinalNode).toList();
        if (finalNodes.isEmpty()) {
            throw new IllegalArgumentException("PipelineGraph must have exactly one finalNode, found 0");
        }
        if (finalNodes.size() > 1) {
            String names = finalNodes.stream().map(GraphNode::getId).collect(Collectors.joining(", "));
            throw new IllegalArgumentException("PipelineGraph must have exactly one finalNode, found "
                    + finalNodes.size() + ": " + names);
        }
        this.finalNodeId = finalNodes.get(0).getId();

        // DFS 环检测
        detectCycles();

        // Kahn 拓扑排序 + 深度计算
        this.depthGroups = computeDepthGroups();
    }

    // ── 查询 ──

    public Map<String, GraphNode> getNodes() { return nodes; }
    public GraphNode getNode(String id) { return nodes.get(id); }
    public String getFinalNodeId() { return finalNodeId; }
    public GraphNode getFinalNode() { return nodes.get(finalNodeId); }
    public int size() { return nodes.size(); }

    /**
     * 返回按深度分组的有序列表。
     * 索引 i 对应的 List 包含所有深度为 i 的节点。
     * 同深度节点无依赖关系，可安全并行执行。
     */
    public List<List<GraphNode>> getDepthGroups() {
        return depthGroups;
    }

    // ── 内部：环检测 ──

    private void detectCycles() {
        Set<String> visited = new HashSet<>();
        Set<String> stack = new HashSet<>();

        for (String id : nodes.keySet()) {
            if (!visited.contains(id)) {
                if (dfsCycle(id, visited, stack, new ArrayList<>())) {
                    throw new IllegalArgumentException("PipelineGraph contains a cycle");
                }
            }
        }
    }

    private boolean dfsCycle(String id, Set<String> visited, Set<String> stack, List<String> path) {
        visited.add(id);
        stack.add(id);
        path.add(id);

        for (String dep : nodes.get(id).getDependencies()) {
            if (!visited.contains(dep)) {
                if (dfsCycle(dep, visited, stack, path)) return true;
            } else if (stack.contains(dep)) {
                return true; // back edge → cycle
            }
        }

        stack.remove(id);
        path.remove(path.size() - 1);
        return false;
    }

    // ── 内部：Kahn 拓扑排序 + 深度计算 ──

    private List<List<GraphNode>> computeDepthGroups() {
        // 计算每个节点的入度
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        Map<String, Integer> depths = new LinkedHashMap<>();
        for (String id : nodes.keySet()) {
            inDegree.put(id, nodes.get(id).getDependencies().size());
            depths.put(id, 0);
        }

        // Kahn 队列
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> e : inDegree.entrySet()) {
            if (e.getValue() == 0) {
                queue.offer(e.getKey());
                depths.put(e.getKey(), 0);
            }
        }

        if (queue.isEmpty()) {
            // 所有节点都有依赖 = 不可能（无 DAG 入口，前面环检测应该已捕获）
            throw new IllegalArgumentException("PipelineGraph has no source nodes (all nodes have dependencies)");
        }

        List<String> topoOrder = new ArrayList<>();
        int maxDepth = 0;

        while (!queue.isEmpty()) {
            String current = queue.poll();
            topoOrder.add(current);
            int currentDepth = depths.get(current);

            // 遍历所有节点，找到依赖 current 的节点
            for (GraphNode node : nodes.values()) {
                if (node.getDependencies().contains(current)) {
                    int newDepth = currentDepth + 1;
                    if (newDepth > depths.get(node.getId())) {
                        depths.put(node.getId(), newDepth);
                        maxDepth = Math.max(maxDepth, newDepth);
                    }
                    inDegree.put(node.getId(), inDegree.get(node.getId()) - 1);
                    if (inDegree.get(node.getId()) == 0) {
                        queue.offer(node.getId());
                    }
                }
            }
        }

        if (topoOrder.size() != nodes.size()) {
            throw new IllegalArgumentException(
                    "PipelineGraph topological sort incomplete: " + topoOrder.size()
                    + " of " + nodes.size() + " nodes sorted (cycle detected)");
        }

        // 按深度分组
        List<List<GraphNode>> groups = new ArrayList<>(maxDepth + 1);
        for (int d = 0; d <= maxDepth; d++) {
            groups.add(new ArrayList<>());
        }
        for (String id : topoOrder) {
            int depth = depths.get(id);
            groups.get(depth).add(nodes.get(id));
        }

        return Collections.unmodifiableList(groups);
    }

    // ── 工厂方法 ──

    /**
     * 线性拓扑：按给定顺序串行排列。
     * 最后一个节点自动设为 finalNode。
     */
    public static PipelineGraph linear(String... ids) {
        if (ids.length == 0) throw new IllegalArgumentException("linear requires at least 1 node");
        List<GraphNode> nodes = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            GraphNode.Builder b = GraphNode.builder(ids[i]).moduleName(ids[i]);
            if (i > 0) b.dependsOn(ids[i - 1]);
            if (i == ids.length - 1) b.finalNode(true);
            nodes.add(b.build());
        }
        return new PipelineGraph(nodes);
    }

    @Override
    public String toString() {
        return "PipelineGraph{nodes=" + size()
                + ", finalNode=" + finalNodeId
                + ", depthGroups=" + depthGroups.size() + '}';
    }
}
