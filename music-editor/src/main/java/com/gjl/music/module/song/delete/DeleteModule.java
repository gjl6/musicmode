package com.gjl.music.module.song.delete;

import com.gjl.music.module.Module;

/**
 * 删除模块接口 —— 支持三种删除模式：
 * <ul>
 *   <li>empty-folders —— 递归清理空文件夹</li>
 *   <li>non-music-folders —— 删除不包含音乐文件的文件夹</li>
 *   <li>selected —— 删除用户勾选的文件和文件夹（含 DB 清理）</li>
 * </ul>
 */
public interface DeleteModule extends Module {
}
