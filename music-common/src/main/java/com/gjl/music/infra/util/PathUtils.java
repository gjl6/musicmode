package com.gjl.music.infra.util;

import java.nio.file.Path;

/**
 * 路径工具 — 统一分隔符归一化 + MySQL LIKE 转义。
 *
 * <h3>设计原则</h3>
 * 项目内所有路径（DB 存储、SQL 查询、Java 比对）统一使用<b>正斜杠 {@code /}</b>格式。
 * 消除 Windows 反斜杠在 MySQL LIKE 中被当作转义字符的问题，保证跨平台一致。
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>{@link #normalize(String)} / {@link #normalize(Path)} — 路径存储、比对、LIKE 查询前缀</li>
 *   <li>{@link #escapeMySqlLike(String)} — 仅当必须保留反斜杠时的 MySQL LIKE 转义</li>
 * </ul>
 */
public final class PathUtils {

    private PathUtils() {
    }

    /**
     * 路径归一化为正斜杠格式。
     * <ul>
     *   <li>Windows: {@code D:\music\song.mp3} → {@code D:/music/song.mp3}</li>
     *   <li>Linux: {@code /music/song.mp3} → 不变</li>
     * </ul>
     */
    public static String normalize(String path) {
        if (path == null) return null;
        return path.replace('\\', '/');
    }

    /** {@link #normalize(String)} 的 Path 重载 */
    public static String normalize(Path path) {
        if (path == null) return null;
        return normalize(path.toString());
    }

    /**
     * MySQL LIKE 转义：双写反斜杠，转义通配符。
     * 仅在必须保留反斜杠格式时使用，一般场景优先用 {@link #normalize(String)}。
     */
    public static String escapeMySqlLike(String s) {
        if (s == null) return null;
        return s.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
