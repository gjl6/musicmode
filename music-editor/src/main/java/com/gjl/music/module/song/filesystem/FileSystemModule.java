package com.gjl.music.module.song.filesystem;

import com.gjl.music.filesystem.BrowseResult;
import com.gjl.music.module.Module;

import java.io.File;

/**
 * 文件系统模块接口 —— 单层目录浏览、路径安全校验、文件属性读取。
 *
 * <p>同时参与 Pipeline 编排和对外提供直接调用能力。</p>
 */
public interface FileSystemModule extends Module {

    /** 浏览指定目录，返回子文件夹列表和音频文件信息列表（单层，不递归） */
    BrowseResult browse(File root, String relativePath);

    /** 将相对路径解析为绝对 File 对象，同时进行安全校验 */
    File resolveFile(File root, String relativePath);

    /** 安全校验：确保目标路径未越出根目录范围 */
    void validatePath(File root, File target);
}
