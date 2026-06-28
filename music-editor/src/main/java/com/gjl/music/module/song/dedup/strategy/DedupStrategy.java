package com.gjl.music.module.song.dedup.strategy;

import com.gjl.music.module.song.dedup.DuplicateGroup;
import com.gjl.music.infra.pipeline.NodeContext;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 独立去重策略接口。
 * 每个实现对应一种去重方式（哈希/文件名/元数据/指纹），互不依赖。
 */
public interface DedupStrategy {

    /** 策略标识: "hash" / "filename" / "metadata" / "fingerprint" */
    String name();

    /** 显示名称 */
    String label();

    /** 执行去重检测，返回重复组列表。ctx 用于按需调用其他模块（如 context.fork） */
    List<DuplicateGroup> detect(List<Path> filePaths, Map<String, Object> options, NodeContext ctx);

    /** 是否为慢速策略（指纹比对），前端据此显示提示 */
    default boolean isSlow() { return false; }
}
