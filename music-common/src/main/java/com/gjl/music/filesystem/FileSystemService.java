package com.gjl.music.filesystem;

import java.io.File;

/**
 * 文件系统浏览服务 —— 单层目录浏览、路径安全校验、文件属性读取。
 *
 * <p>纯业务逻辑，无 Pipeline 依赖。可被 editor/pipeline 或 playback/auth 直接使用。
 */
public interface FileSystemService {

    /** 浏览指定目录，返回子文件夹列表和音频文件信息列表（单层，不递归） */
    BrowseResult browse(File root, String relativePath);

    /** 将相对路径解析为绝对 File 对象，同时进行安全校验 */
    File resolveFile(File root, String relativePath);

    /** 安全校验：确保目标路径未越出根目录范围 */
    void validatePath(File root, File target);
}
