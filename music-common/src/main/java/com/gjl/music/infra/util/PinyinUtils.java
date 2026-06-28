package com.gjl.music.infra.util;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

/**
 * 中文拼音转换工具，用于歌曲标题排序键（sort_title）的生成。
 *
 * <h3>转换规则</h3>
 * <ul>
 *   <li>中文字符 → 全拼大写（"千里之外" → "QIANLIZHIWAI"）</li>
 *   <li>英文字母 → 大写（"Hello" → "HELLO"）</li>
 *   <li>数字 → 保持原样（"99" → "99"）</li>
 *   <li>其他字符 → 保持原样</li>
 * </ul>
 */
public final class PinyinUtils {

    private static final HanyuPinyinOutputFormat FORMAT = new HanyuPinyinOutputFormat();

    static {
        FORMAT.setCaseType(HanyuPinyinCaseType.UPPERCASE);
        FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        FORMAT.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    private PinyinUtils() {}

    /**
     * 将歌曲标题转换为排序键。
     * 返回结果首字符用于字母索引分组（A-Z、0-9、#）。
     *
     * @param title 原始标题，可为 null
     * @return 排序键，首字符 = 分组字母；null 或空字符串返回 "#"
     */
    public static String toSortKey(String title) {
        if (title == null || title.isBlank()) return "#";
        String trimmed = title.trim();
        StringBuilder sb = new StringBuilder(trimmed.length() * 4);
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            String py = toSinglePinyin(c);
            if (py != null) {
                sb.append(py);
            } else if (c >= 'a' && c <= 'z') {
                sb.append((char) (c - 32));  // 小写 → 大写
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 单字符转拼音大写，非 CJK 返回 null */
    private static String toSinglePinyin(char c) {
        // CJK Unified: U+4E00–U+9FFF, Extension A: U+3400–U+4DBF
        if ((c < 0x4E00 || c > 0x9FFF) && (c < 0x3400 || c > 0x4DBF)) {
            return null;
        }
        try {
            String[] arr = PinyinHelper.toHanyuPinyinStringArray(c, FORMAT);
            return (arr != null && arr.length > 0) ? arr[0] : null;
        } catch (BadHanyuPinyinOutputFormatCombination e) {
            return null;  // 不应该发生，FORMAT 是预初始化的
        }
    }
}
