package com.gjl.music.module.song.cuesplit;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CUE 文件解析器 —— 将 CUE 文件文本解析为 {@link CueSheet} 数据模型。
 *
 * <p>支持的 CUE 命令：PERFORMER, TITLE, FILE, TRACK, INDEX
 * <p>时间格式：MM:SS:FF（75 帧/秒 = 每帧 13.333ms）
 * <p>编码：UTF-8 优先，含乱码时回退 GBK 并尝试编码修复。
 */
@Slf4j
public class CueParser {

    // CUE 关键字正则
    private static final Pattern RE_PERFORMER  = Pattern.compile("^PERFORMER\\s+\"(.+)\"\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_TITLE      = Pattern.compile("^TITLE\\s+\"(.+)\"\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_FILE       = Pattern.compile("^FILE\\s+\"(.+)\"\\s+(\\w+)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_TRACK      = Pattern.compile("^TRACK\\s+(\\d{1,2})\\s+(\\w+)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_INDEX      = Pattern.compile("^INDEX\\s+(\\d{2})\\s+(\\d{2}):(\\d{2}):(\\d{2})\\s*$", Pattern.CASE_INSENSITIVE);

    private static final int FRAMES_PER_SEC = 75;
    private static final int MS_PER_FRAME   = 1000 / FRAMES_PER_SEC; // ~13ms

    private CueParser() { throw new AssertionError("Utility class"); }

    /**
     * 解析 CUE 文件。
     * @param cuePath CUE 文件路径
     * @return 解析后的 CueSheet
     * @throws IOException 文件读取失败
     * @throws CueParseException 语法错误
     */
    public static CueSheet parse(Path cuePath) throws IOException, CueParseException {
        String content = readWithEncoding(cuePath);
        return parseContent(content);
    }

    /**
     * 从字符串内容解析 CUE。
     */
    public static CueSheet parseContent(String content) throws CueParseException {
        CueSheet sheet = new CueSheet();
        CueSheet.CueFile currentFile = null;
        CueSheet.CueTrack currentTrack = null;

        String[] lines = content.split("\\r?\\n");
        for (int lineNum = 0; lineNum < lines.length; lineNum++) {
            String rawLine = lines[lineNum];
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("REM")) continue;

            try {
                // PERFORMER
                Matcher mPerf = RE_PERFORMER.matcher(line);
                if (mPerf.matches()) {
                    String val = mPerf.group(1);
                    if (currentTrack != null) {
                        currentTrack.setPerformer(val);
                    } else {
                        sheet.setPerformer(val);
                    }
                    continue;
                }

                // TITLE
                Matcher mTitle = RE_TITLE.matcher(line);
                if (mTitle.matches()) {
                    String val = mTitle.group(1);
                    if (currentTrack != null) {
                        currentTrack.setTitle(val);
                    } else {
                        sheet.setTitle(val);
                    }
                    continue;
                }

                // FILE
                Matcher mFile = RE_FILE.matcher(line);
                if (mFile.matches()) {
                    currentFile = new CueSheet.CueFile();
                    currentFile.setFileName(mFile.group(1));
                    currentFile.setFileType(mFile.group(2).toUpperCase());
                    sheet.addFile(currentFile);
                    currentTrack = null;
                    continue;
                }

                // TRACK
                Matcher mTrack = RE_TRACK.matcher(line);
                if (mTrack.matches()) {
                    if (currentFile == null) {
                        // CUE 中 TRACK 出现在 FILE 之前——隐式单文件
                        currentFile = new CueSheet.CueFile();
                        currentFile.setFileType("WAVE");
                        sheet.addFile(currentFile);
                    }
                    currentTrack = new CueSheet.CueTrack();
                    currentTrack.setTrackNumber(Integer.parseInt(mTrack.group(1)));
                    currentTrack.setType(mTrack.group(2).toUpperCase());
                    currentFile.addTrack(currentTrack);
                    continue;
                }

                // INDEX
                Matcher mIndex = RE_INDEX.matcher(line);
                if (mIndex.matches()) {
                    if (currentTrack == null) {
                        throw new CueParseException("行 " + (lineNum + 1) + ": INDEX 出现在 TRACK 之前");
                    }
                    int indexNum = Integer.parseInt(mIndex.group(1));
                    int mm = Integer.parseInt(mIndex.group(2));
                    int ss = Integer.parseInt(mIndex.group(3));
                    int ff = Integer.parseInt(mIndex.group(4));
                    int totalMs = (mm * 60 + ss) * 1000 + ff * MS_PER_FRAME;

                    if (indexNum == 0) {
                        currentTrack.setIndex00Ms(totalMs);
                    } else if (indexNum == 1) {
                        currentTrack.setIndex01Ms(totalMs);
                    }
                    continue;
                }

                // 未知行忽略
                log.debug("CUE 忽略未知行 {}: {}", lineNum + 1, line);

            } catch (CueParseException e) {
                throw e;
            } catch (Exception e) {
                throw new CueParseException("行 " + (lineNum + 1) + " 解析失败: " + line, e);
            }
        }

        // 后处理：为没有 INDEX 01 的 TRACK 推断结束时间
        postProcess(sheet);

        return sheet;
    }

    // ── 编码处理 ──

    /**
     * 读取 CUE 文件内容，自动检测编码。
     * UTF-8 优先 → 检测 CUE 关键字是否存在 → 不存在则回退 GBK。
     */
    private static String readWithEncoding(Path cuePath) throws IOException {
        byte[] raw = Files.readAllBytes(cuePath);

        // 跳过 BOM
        int offset = 0;
        if (raw.length >= 3 && raw[0] == (byte)0xEF && raw[1] == (byte)0xBB && raw[2] == (byte)0xBF) {
            offset = 3;
        }

        String utf8 = new String(raw, offset, raw.length - offset, StandardCharsets.UTF_8);
        if (containsCueKeywords(utf8)) {
            return utf8;
        }

        // UTF-8 不含 CUE 关键字 → 尝试 GBK（中文 CUE 常见编码）
        log.debug("UTF-8 解码无 CUE 关键字，回退 GBK: {}", cuePath.getFileName());
        String gbk = new String(raw, offset, raw.length - offset, Charset.forName("GBK"));
        if (containsCueKeywords(gbk)) {
            return gbk;
        }

        // GBK 也不行 → 返回 UTF-8 原文尝试（可能文件本身就缺少关键字）
        return utf8;
    }

    /** 检查文本是否包含 CUE 关键字（至少出现一个即认为编码正确） */
    private static boolean containsCueKeywords(String text) {
        return text != null && (text.contains("TRACK") || text.contains("FILE")
                || text.contains("TITLE") || text.contains("PERFORMER"));
    }

    // ── 后处理 ──

    /**
     * 计算各 TRACK 的结束时间（= 下一 TRACK 的 INDEX 01），
     * 并填充每 FILE 内 TRACK 间的 duration。
     */
    private static void postProcess(CueSheet sheet) {
        for (CueSheet.CueFile file : sheet.getFiles()) {
            List<CueSheet.CueTrack> tracks = file.getTracks();
            for (int i = 0; i < tracks.size(); i++) {
                CueSheet.CueTrack t = tracks.get(i);
                // 确保 INDEX 01 存在
                if (t.getIndex01Ms() < 0) {
                    log.warn("TRACK {:02d} 缺少 INDEX 01，设为 0", t.getTrackNumber());
                    t.setIndex01Ms(0);
                }
            }
        }
    }

    // ── 异常 ──

    /** CUE 解析异常 */
    public static class CueParseException extends Exception {
        public CueParseException(String message) {
            super(message);
        }
        public CueParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
