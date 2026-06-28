package com.gjl.music.infra.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** 音频文件类型判断工具 —— 统一的音频扩展名匹配和文件验证 */
public final class AudioFileUtils {

    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
            "mp3", "flac", "wav", "ogg", "wma", "m4a", "aac", "ape", "wv", "opus", "mp4");

    private AudioFileUtils() { throw new AssertionError("Utility class"); }

    /** 判断路径是否是支持的音频文件（检查扩展名 + 是否为常规文件） */
    public static boolean isAudioFile(Path path) {
        if (path == null || !Files.isRegularFile(path)) return false;
        return AUDIO_EXTENSIONS.contains(extension(path));
    }

    /** 从文件名提取小写扩展名，不含点号 */
    public static String extension(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1) : "";
    }

    /** 判断是否是支持的音频文件（java.io.File 版本） */
    public static boolean isAudioFile(java.io.File file) {
        return file != null && isAudioFile(file.toPath());
    }
}
