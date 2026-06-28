package com.gjl.music.module.album.support;

import com.gjl.music.module.FailurePolicy;
import com.gjl.music.module.Module;
import com.gjl.music.infra.pipeline.NodeContext;
import com.gjl.music.infra.pipeline.NodeHandler;
import com.gjl.music.infra.pipeline.NodeResult;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Album 模块轻量基类 ???封装 Pipeline 模块通用样板??? *
 * <h3>???Artist ArtistModuleSupport 的区???/h3>
 * 完全对标 Artist 模块???{@code ArtistModuleSupport}。Album 管道不需要文件批处理—??? * 专辑已在 DB 中，模块只需：读输入 ???遍历 ???逐项处理??? *
 * <h3>基类职责</h3>
 * <ol>
 *   <li>???{@code ctx.getSlot("options")} ???{@link #name()} 取子配置 ???{@link #configure(Map)}</li>
 *   <li>调用 {@link #readInput(NodeContext)} 获取待处理项列表（默认读 {@code node.album-scanner.output}???/li>
 *   <li>遍历每项：{@code checkPause ???processOne ???reportItemComplete}，异常按 {@link #failurePolicy()} 处理</li>
 * </ol>
 *
 * <h3>子类需覆写</h3>
 * <ul>
 *   <li>{@link #processOne(Object, NodeContext)} ???<b>必须</b>，返回可选的状态描述字符串</li>
 *   <li>{@link #configure(Map)} ???可选，接收 options 中本模块的子配置</li>
 *   <li>{@link #readInput(NodeContext)} ???可选，覆盖默认输入来源</li>
 *   <li>{@link #itemLabel(Object)} ???可选，自定义进度标???/li>
 * </ul>
 *
 * @see com.gjl.music.module.artist.support.ArtistModuleSupport
 */
@Slf4j
public abstract class AlbumModuleSupport implements Module, NodeHandler {

    // ── 子类必须覆写 ──

    /**
     * 处理单个输入项???     *
     * @param item 输入项（默认来自 {@code node.album-scanner.output}，通常???{@link Long} albumId???     * @param ctx  节点上下???     * @return 可选状态描述（???"已增?????????规范化名"），???null 则不追加
     * @throws Exception 失败时抛出，基类???failurePolicy 处理
     */
    protected abstract String processOne(Object item, NodeContext ctx) throws Exception;

    // ── 子类可选覆???──

    /**
     * 接收 options 中本模块的子配置???     * 基类???{@link #execute(NodeContext)} 开头自动从 {@code ctx.getSlot("options")}
     * 中提???{@code options[name()]} 并调用此方法???     */
    @Override
    public void configure(Map<String, Object> config) {
        // 默认空实现，子类按需覆写
    }

    /**
     * 读取待处理项列表。默认从 {@code node.album-scanner.output} slot 读取???     * 子类可覆写以支持其他输入来源（如 album-merge ???{@code merge.groups} 读取）???     */
    protected List<?> readInput(NodeContext ctx) {
        return ctx.getSlot("node.album-scanner.output");
    }

    /**
     * 为进度上报生成可读标签。默认调???{@code String.valueOf(item)}???     */
    protected String itemLabel(Object item) {
        return String.valueOf(item);
    }

    // ── NodeHandler 统一入口 ──

    @Override
    public NodeResult execute(NodeContext ctx) throws Exception {
        // 从上下文数据流加载模块配置
        Map<String, Object> allOptions = ctx.getSlot("options");
        log.info("{}: ctx.getSlot(\"options\") = {}", name(),
                allOptions != null ? allOptions.keySet() : "null");
        if (allOptions != null) {
            Object cfg = allOptions.get(name());
            log.info("{}: allOptions.get(\"{}\") = {} (type={})", name(), name(),
                    cfg, cfg != null ? cfg.getClass().getSimpleName() : "null");
            if (cfg instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> config = (Map<String, Object>) map;
                log.info("{}: calling configure({})", name(), config.keySet());
                configure(config);
            } else {
                log.warn("{}: cfg is not a Map, skipping configure()", name());
            }
        } else {
            log.warn("{}: ctx.getSlot(\"options\") 为 null，前端未传入模块配置！", name());
        }

        // 读取输入
        List<?> items = readInput(ctx);
        if (items == null || items.isEmpty()) {
            log.warn("{}: 无输入项，跳过", name());
            return new NodeResult();
        }

        // 开始遍历处理
        NodeResult result = new NodeResult();
        ctx.setSlot("node." + name() + ".total", items.size());
        log.info("{}: 开始处理 {} 项", name(), items.size());

        for (Object item : items) {
            ctx.checkPause();
            String label = itemLabel(item);
            try {
                String detail = processOne(item, ctx);
                String fullLabel = detail != null ? label + " (" + detail + ")" : label;
                ctx.reportItemComplete(fullLabel, true, null);
                result.addItemResult(fullLabel, true, null);
            } catch (Exception e) {
                log.error("{}: {} 处理失败: {}", name(), label, e.getMessage(), e);
                ctx.reportItemComplete(label, false, e.getMessage());
                result.addItemResult(label, false, e.getMessage());
                if (failurePolicy() == FailurePolicy.FAIL_FAST) {
                    throw e;
                }
                // SKIP / RETRY: 继续下一项
            }
        }

        log.info("{}: 完成, success={}, failed={}",
                name(), result.successCount(), result.failedCount());
        return result;
    }
}
