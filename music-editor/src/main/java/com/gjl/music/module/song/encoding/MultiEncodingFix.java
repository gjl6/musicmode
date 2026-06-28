package com.gjl.music.module.song.encoding;

import lombok.extern.slf4j.Slf4j;
import org.mozilla.universalchardet.UniversalDetector;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 多编码修复器 —— 检测并修复音乐元数据中的编码乱码。
 *
 * <p>核心流程：
 * <ol>
 *   <li>判断输入是否含乱码特征（Latin-1 高位字符 0x80-0xFF）</li>
 *   <li>用 ISO-8859-1 / Windows-1252 反转为原始字节</li>
 *   <li>分别用检测编码 + 目标编码列表重新解码</li>
 *   <li>去重 + 排除仍含乱码的 + 评分取最优</li>
 * </ol>
 */
@Slf4j
public class MultiEncodingFix {

    private static final List<String> TARGET_CHARSETS = List.of("GB18030", "GBK", "GB2312", "UTF-8");
    private static final List<String> REVERSAL_CHARSETS = List.of("ISO-8859-1", "windows-1252");

    private MultiEncodingFix() { throw new AssertionError("Utility class"); }

    // ── 编码检测 ──

    public static String guessEncoding(byte[] raw) {
        if (raw == null || raw.length == 0) return null;
        UniversalDetector det = new UniversalDetector(null);
        det.handleData(raw, 0, raw.length);
        det.dataEnd();
        return det.getDetectedCharset();
    }

    // ── 主入口 ──

    /**
     * 检测并修复乱码。正常文本原样返回。
     */
    public static String processInput(String input) {
        if (input == null || input.isEmpty()) return input;
        if (!isGarbled(input)) return input;

        List<Candidate> candidates = new ArrayList<>();

        for (String reversalCs : REVERSAL_CHARSETS) {
            byte[] bytes;
            try {
                bytes = input.getBytes(Charset.forName(reversalCs));
            } catch (Exception e) {
                continue;
            }

            // 检测到的编码优先尝试
            String detected = guessEncoding(bytes);
            if (detected != null && !detected.isEmpty()
                    && !TARGET_CHARSETS.contains(detected)) {
                tryDecode(candidates, bytes, detected, input);
            }

            // 全部目标编码
            for (String targetCs : TARGET_CHARSETS) {
                tryDecode(candidates, bytes, targetCs, input);
            }
        }

        // 去重 + 排除仍含 Latin-1 高位字符或替换符过多的候选
        Set<String> seen = new LinkedHashSet<>();
        List<Candidate> unique = new ArrayList<>();
        for (Candidate c : candidates) {
            if (hasLatinHighChars(c.decoded)) continue;
            if (countReplacement(c.decoded) > 3) continue;
            if (seen.add(c.decoded)) unique.add(c);
        }
        unique.sort(Comparator.comparingInt(Candidate::score).reversed());

        if (unique.isEmpty()) {
            log.debug("EncodingRepair: 未找到有效修复候选，保留原文");
            return input;
        }

        Candidate best = unique.get(0);
        log.debug("修复: [{}] → [{}] score={}", input, best.decoded, best.score);
        return best.decoded;
    }

    // ── 内部 ──

    private static void tryDecode(List<Candidate> candidates, byte[] bytes,
                                  String charset, String input) {
        try {
            String decoded = new String(bytes, charset);
            if (isValidResult(decoded)) {
                candidates.add(new Candidate(decoded, score(decoded, input)));
            }
        } catch (Exception ignored) {
            // 不完整尾部字节：去掉 GBK 首字节后重试
            if (bytes.length > 1 && isGBLeadByte(bytes[bytes.length - 1])) {
                try {
                    byte[] trimmed = Arrays.copyOf(bytes, bytes.length - 1);
                    String decoded = new String(trimmed, charset);
                    if (isValidResult(decoded)) {
                        candidates.add(new Candidate(decoded, score(decoded, input) - 5));
                    }
                } catch (Exception ignored2) {
                    // GBK fallback decode also failed, no more retries
                }
            }
        }
    }

    private static boolean isGBLeadByte(byte b) {
        int u = b & 0xFF;
        return u >= 0x81 && u <= 0xFE;
    }

    /**
     * 候选评分：
     * <ul>
     *   <li>中文/可识别语言占比 0-100 分</li>
     *   <li>无替换符(�?) +20 分</li>
     *   <li>长度与输入一致（未丢字节） +10 分</li>
     * </ul>
     */
    private static int score(String decoded, String input) {
        int s = 0;
        Map<Language, Integer> ratio = getLanguageRatio(decoded);
        if (!ratio.isEmpty()) {
            s += ratio.values().stream().findFirst().orElse(0);
        }
        if (countReplacement(decoded) == 0) s += 20;
        if (decoded.length() == input.length()) s += 10;
        return s;
    }

    /**
     * 是否含乱码特征：C1 控制字符 (0x80-0x9F)、Latin-1 高位可打印字符或 Unicode 替换符。
     * 排除 Unicode 不可见空格/格式字符（如 \\u00A0 不换行空格），它们不是编码损坏。
     */
    static boolean isGarbled(String s) {
        if (s == null || s.isEmpty()) return false;
        for (char c : s.toCharArray()) {
            if (c >= 0x80 && c <= 0x9F) return true;       // C1 控制码（强编码损坏特征）
            // 0xA0-0xFF 可打印字符，排除不是编码损坏的 Unicode 空格/格式符
            if (c >= 0xA0 && c <= 0xFF
                    && c != ' '   // NO-BREAK SPACE
                    && c != '­')  // SOFT HYPHEN
                return true;
            if (c == '�' || c == '\0') return true;          // 替换符/空字符
        }
        return false;
    }

    /** 是否含 Latin-1 高位字符 —— 候选过滤时用（不含替换符检查，避免误杀有效中文） */
    private static boolean hasLatinHighChars(String s) {
        if (s == null || s.isEmpty()) return false;
        for (char c : s.toCharArray()) {
            if (c >= 0x80 && c <= 0xFF) return true;
        }
        return false;
    }

    /** 初步校验：非空 + 可识别语言占比 ≥ 30% + 替换符数 ≤ 3 */
    static boolean isValidResult(String decoded) {
        if (decoded == null || decoded.isEmpty()) return false;
        if (countReplacement(decoded) > 3) return false;
        Map<Language, Integer> ratio = getLanguageRatio(decoded);
        if (ratio.isEmpty()) return false;
        int topRatio = ratio.values().stream().findFirst().orElse(0);
        // 必须中文（纯 ASCII 由 isGarbled 预检直接返回）
        if (!ratio.containsKey(Language.CHINESE)) return false;
        return topRatio >= 30;
    }

    static int countReplacement(String decoded) {
        if (decoded == null) return 0;
        int count = 0;
        for (char c : decoded.toCharArray()) {
            if (c == '�' || c == '?' || c == '\0') count++;
        }
        return count;
    }

    static Map<Language, Integer> getLanguageRatio(String decoded) {
        Map<Language, Integer> langMap = MusicCharValidator.getLanguagesWithCount(decoded);
        if (langMap.isEmpty()) return Collections.emptyMap();

        Map<Language, Integer> sorted = langMap.entrySet().stream()
                .sorted(Map.Entry.<Language, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));

        int total = decoded.length();
        if (total == 0) return Collections.emptyMap();

        Map.Entry<Language, Integer> top = sorted.entrySet().stream().findFirst().orElse(null);
        if (top == null) return Collections.emptyMap();

        Language topLang = top.getKey();
        int topCount = top.getValue();
        int ratio = (topCount * 100) / total;

        if (ratio <= 70) {
            if (topLang != Language.ENGLISH) {
                int en = sorted.getOrDefault(Language.ENGLISH, 0);
                int sym = sorted.getOrDefault(Language.MUSIC_SYMBOL, 0);
                ratio = ((topCount + en + sym) * 100) / total;
            } else {
                int second = sorted.entrySet().stream().skip(1).findFirst()
                        .map(Map.Entry::getValue).orElse(0);
                int sym = sorted.getOrDefault(Language.MUSIC_SYMBOL, 0);
                ratio += ((second + sym) * 100) / total;
            }
        }

        return Map.of(topLang, ratio);
    }

    // ── 内部类型 ──

    private record Candidate(String decoded, int score) {}
}
