package com.gjl.music.module.song.dbsync;

import com.gjl.music.module.Module;

/**
 * 数据库同步模块 —— 确保 DB 中的文件记录与磁盘实际文件一致。
 *
 * <p>在 Scanner/FileSystem 产出文件列表后立即执行，
 * 删除 DB 中磁盘上已不存在的记录，保证后续模块查询的是最新状态。
 */
public interface DbSyncModule extends Module {
}
