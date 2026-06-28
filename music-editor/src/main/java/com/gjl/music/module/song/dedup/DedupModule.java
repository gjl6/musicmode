package com.gjl.music.module.song.dedup;

import com.gjl.music.module.Module;

/**
 * 重复文件检测模块接口。
 *
 * <p>支持 4 种独立策略，通过 {@link Module#configure(java.util.Map)} 传入 options 选择：
 * <pre>{@code
 * "dedup": {
 *   "strategies": ["hash", "filename", "metadata", "fingerprint"],
 *   "metadataMode": "standard",
 *   "fingerprint": { "threshold": 0.80 }
 * }
 * }</pre>
 * 每个策略独立产出 DuplicateGroup 列表，结果按策略名分组存入 NodeContext。
 */
public interface DedupModule extends Module {
}
