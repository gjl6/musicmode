package com.gjl.music.module.artist.scanner;

import com.gjl.music.module.Module;

/**
 * 艺术家扫描模块 —— Pipeline 源节点，根据选择规则从 DB 查询艺术家 ID 列表。
 *
 * <p>对标 song 管道的 {@code scanner} 模块：song 扫描文件系统 → List&lt;Path&gt;，
 * artist-scanner 查询数据库 → List&lt;Long&gt;。
 *
 * <p>输入：从 NodeContext 读取 {@code artist.selection}（ArtistSelection）
 * <br>输出：写入 {@code node.artist-scanner.output}（List&lt;Long&gt; artist IDs）
 */
public interface ArtistScannerModule extends Module {
}
