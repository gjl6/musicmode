package com.gjl.music.infra.util;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;


public final class PinyinUtils {

    private static final HanyuPinyinOutputFormat FORMAT = new HanyuPinyinOutputFormat();

    static {
        FORMAT.setCaseType(HanyuPinyinCaseType.UPPERCASE);
        FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        FORMAT.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    private PinyinUtils() {}


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
                sb.append((char) (c - 32));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }


    private static String toSinglePinyin(char c) {
                if ((c < 0x4E00 || c > 0x9FFF) && (c < 0x3400 || c > 0x4DBF)) {
            return null;
        }
        try {
            String[] arr = PinyinHelper.toHanyuPinyinStringArray(c, FORMAT);
            return (arr != null && arr.length > 0) ? arr[0] : null;
        } catch (BadHanyuPinyinOutputFormatCombination e) {
            return null;
        }
    }
}
