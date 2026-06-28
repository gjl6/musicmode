package com.gjl.music.module.song.dboperator;

import com.gjl.music.module.Module;

/**
 * 数据库操作模块接口 —— Pipeline 模块。
 *
 * <p>批量持久化能力由 {@link com.gjl.music.persistence.MetadataPersister}（common 层）提供，
 * 通过组合注入使用。</p>
 */
public interface DbOperatorModule extends Module {

    /** 确保数据库表结构存在（幂等） */
    void ensureTables();
}
