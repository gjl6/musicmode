package com.gjl.music.playback.infra.m3u;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;


public final class M3uParser {

    private M3uParser() {}


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
                                        entries.add(new M3uEntry(line, currentTitle, currentDuration));
                    currentTitle = null;
                    currentDuration = -1;
                }
                            }
        } catch (Exception e) {
            throw new RuntimeException("M3U 解析失败: " + e.getMessage(), e);
        }

        return entries;
    }


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


    public record M3uEntry(String path, String title, int duration) {}
}
