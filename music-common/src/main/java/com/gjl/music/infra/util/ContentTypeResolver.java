package com.gjl.music.infra.util;

import java.util.Locale;

/**
 * 文件扩展名 → MIME Type 映射。
 *
 * <p>与 {@link FfmpegUtil#mimeTypeForFormat} 互补：
 * 本类用于原始文件（基于文件扩展名），FfmpegUtil 用于转码流（基于目标格式名）。
 */
public final class ContentTypeResolver {

    private ContentTypeResolver() {}

    /**
     * 根据文件扩展名推断 MIME Type。
     */
    public static String resolve(String fileName) {
        if (fileName == null) return "application/octet-stream";
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".mp3"))  return "audio/mpeg";
        if (name.endsWith(".flac")) return "audio/flac";
        if (name.endsWith(".wav"))  return "audio/wav";
        if (name.endsWith(".mp4") || name.endsWith(".m4a") || name.endsWith(".aac")) return "audio/mp4";
        if (name.endsWith(".ogg") || name.endsWith(".opus")) return "audio/ogg";
        if (name.endsWith(".wma"))  return "audio/x-ms-wma";
        if (name.endsWith(".aiff") || name.endsWith(".aif")) return "audio/aiff";
        if (name.endsWith(".wv"))   return "audio/x-wavpack";
        if (name.endsWith(".ape"))  return "audio/x-ape";
        return "application/octet-stream";
    }
}
