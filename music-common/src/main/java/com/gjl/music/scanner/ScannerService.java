package com.gjl.music.scanner;

import java.nio.file.Path;
import java.util.List;

/**
 * 文件扫描服务 —— 递归扫描目录，找出所有支持的音频文件。
 *
 * <p>纯业务逻辑，无 Pipeline 依赖。支持流式和批量模式，可被 editor/pipeline 或 playback/auth 直接使用。
 */
public interface ScannerService {

    /** 递归扫描一个或多个目录，返回所有音频文件的绝对路径 */
    List<Path> scan(Path... roots);

    /** 判断文件是否为支持的音频格式 */
    boolean isAudioFile(Path path);

    /** 估算目录文件数（3 层采样） */
    int estimateFileCount(Path dir) throws java.io.IOException;
}
