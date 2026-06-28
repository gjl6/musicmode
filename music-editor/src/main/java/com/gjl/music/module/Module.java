package com.gjl.music.module;

import java.util.List;
import java.util.Map;

/**
 * 模块标识接口 —— 自描述元数据 + 配置 schema。
 * execute 已迁移至 NodeHandler。
 */
public interface Module {

    enum ExecutorType { VIRTUAL, PLATFORM }

    /** 模块唯一标识 */
    String name();

    /** 管道构建时回调，传入本模块的 options */
    default void configure(Map<String, Object> options) {}

    /** 错误处理策略 */
    FailurePolicy failurePolicy();

    /** 线程类型偏好 */
    default ExecutorType executorType() { return ExecutorType.PLATFORM; }

    // ── 自描述元数据（供前端动态渲染）──

    /** 前端展示用中文标签，默认返回 name() */
    default String label() { return name(); }

    /**
     * 是否对用户可见（可作为自动任务步骤选择）。
     * 默认 true；infra 模块（scanner、db-operator、writer 等内部辅助）覆写为 false。
     */
    default boolean isUserVisible() { return true; }

    /**
     * 配置字段描述列表，前端据此动态渲染表单。无配置返回空列表。
     * 每个元素为 Map，包含：key, label, type, default, options(可选)
     * <p>type: string / number / boolean / select / multiselect / array
     */
    default List<Map<String, Object>> configSchema() { return List.of(); }
}
