package com.gjl.music.playback.infra.transcoding;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * 转码缓存接口。
 *
 * <p>缓存转码后的音频流，避免重复转码。
 */
public interface TranscodingCache {

    /**
     * 获取或计算缓存项。
     *
     * @param cacheKey    缓存键
     * @param transcoder  转码函数（返回 InputStream），仅在缓存未命中时调用
     * @return 缓存文件的路径
     */
    Path getOrCompute(String cacheKey, Supplier<InputStream> transcoder);

    /** 当前缓存占用大小（字节） */
    long getCurrentSize();

    /** 缓存文件数量 */
    int getFileCount();

    /** 手动驱逐最旧条目直到满足目标大小 */
    void evictToTarget(long targetBytes);
}
