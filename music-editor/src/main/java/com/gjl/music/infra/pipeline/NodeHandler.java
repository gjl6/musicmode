package com.gjl.music.infra.pipeline;

/**
 * 统一节点处理器接口 —— 所有业务模块实现此接口。
 *
 * <p>替代旧 Module 的 produce/process/flush/execute 四种执行模式。
 * 流式/批量由节点内部自行决定，框架不区分。
 */
@FunctionalInterface
public interface NodeHandler {

    /**
     * 执行节点逻辑。
     *
     * @param ctx 执行上下文（Slot 读写、跨模块调用、暂停检测）
     * @return 执行结果（逐文件 ItemResult + 跨节点 outputs）
     */
    NodeResult execute(NodeContext ctx) throws Exception;
}
