package com.gjl.music.playback.infra.m3u;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * M3U/M3U8 播放列表文件解析器。
 *
 * <p>支持：
 * <ul>
 *   <li>{@code #EXTM3U} 头部</li>
 *   <li>{@code #EXTINF:duration,title} 扩展信息</li>
 *   <li>绝对路径和相对路径</li>
 *   <li>跳过空行和注释行（以 # 开头）</li>
 * </ul>
 */
public final class M3uParser {

    private M3uParser() {}

    /**
     * 解析 M3U 内容，提取文件路径列表。
     *
     * @param m3uContent M3U 文件内容
     * @return 文件路径列表（保持顺序）
     */
    public static List<M3uEntry> parse(String m3uContent) {
        List<M3uEntry> entries = new ArrayList<>();
        String currentTitle = null;
        int currentDuration = -1;

        try (BufferedReader reader = new BufferedReader(new StringReader(m3uContent))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("#EXTINF:")) {
                    // #EXTINF:duration,title
                    String info = line.substring(8);
                    int commaIdx = info.indexOf(',');
                    if (commaIdx >= 0) {
                        try {
                            currentDuration = Integer.parseInt(info.substring(0, commaIdx).trim());
                        } catch (NumberFormatException e) {
                            currentDuration = -1;
                        }
                        currentTitle = info.substring(commaIdx + 1).trim();
                        if (currentTitle.isEmpty()) currentTitle = null;
                    }
                } else if (!line.startsWith("#")) {
                    // 文件路径行
                    entries.add(new M3uEntry(line, currentTitle, currentDuration));
                    currentTitle = null;
                    currentDuration = -1;
                }
                // 跳过 #EXTM3U 和其他注释行
            }
        } catch (Exception e) {
            throw new RuntimeException("M3U 解析失败: " + e.getMessage(), e);
        }

        return entries;
    }

    /**
     * 将路径列表导出为 M3U8 格式。
     *
     * @param entries M3U 条目
     * @return M3U8 内容
     */
    public static String toM3u8(List<M3uEntry> entries) {
        StringBuilder sb = new StringBuilder("#EXTM3U\n");
        for (M3uEntry e : entries) {
            if (e.title != null) {
                sb.append("#EXTINF:").append(Math.max(0, e.duration)).append(",")
                        .append(e.title).append("\n");
            }
            sb.append(e.path).append("\n");
        }
        return sb.toString();
    }

    /**
     * M3U 条目。
     *
     * @param path     文件路径
     * @param title    歌曲标题（可能为 null）
     * @param duration 时长（秒），-1 表示未知
     */
    public record M3uEntry(String path, String title, int duration) {}
}
