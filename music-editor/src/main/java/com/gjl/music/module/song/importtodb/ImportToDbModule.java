package com.gjl.music.module.song.importtodb;

import com.gjl.music.module.Module;

/**
 * 导入到数据库模块 —— 将选中的音乐文件扫描、解析标签并写入数据库。
 *
 * <p>实现类继承 {@link com.gjl.music.module.song.support.MetadataGapFillingModule}，
 * 复用其完整的"文件获取→解析→入库→进度上报"流程。</p>
 */
public interface ImportToDbModule extends Module {
}
