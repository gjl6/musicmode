package com.gjl.music.module.song.scanner;

import com.gjl.music.module.Module;

import java.nio.file.Path;
import java.util.List;

/**
 * 文件扫描模块接口 —— 递归扫描目录，找出所有支持的音频文件。
 *
 * <p>支持 Pipeline 流式数据源模式和批量模式，同时对外提供直接调用能力。</p>
 */
public interface ScannerModule extends Module {

    /** 递归扫描一个或多个目录，返回所有音频文件的绝对路径 */
    List<Path> scan(Path... roots);

    /** 判断文件是否为支持的音频格式 */
    boolean isAudioFile(Path path);
}
