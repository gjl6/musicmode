package com.gjl.music.module.song.encoding;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 音乐标题多语言字符验证工具。
 * 用于判断字符串是否包含合法的音乐标题字符（中/英/日/韩/俄/法/德），
 * 以及统计各语言字符占比——供乱码检测算法使用。
 */
@Slf4j
public class MusicCharValidator {

    private static final Set<Character> ENGLISH_COMMON_CHARS;
    private static final Set<Integer> FRENCH_SPECIFIC_UNICODES = Set.of(
            0x00E9, 0x00E0, 0x00E8, 0x00E7, 0x00FB, 0x00E2, 0x00EE, 0x00EA, 0x00F4);
    private static final Set<Integer> GERMAN_SPECIFIC_UNICODES = Set.of(
            0x00E4, 0x00F6, 0x00FC, 0x00DF, 0x00C4, 0x00D6, 0x00DC);
    private static final Set<Integer> MUSIC_SYMBOL_UNICODES = Set.of(
            0x266A, 0x266B, 0x266C, 0x266D, 0x266E, 0x266F,
            0x1F3B5, 0x1F3B6, 0x1F3A4, 0x1F3A7);

    static {
        Set<Character> tempSet = new HashSet<>();
        for (char c = 'A'; c <= 'Z'; c++) tempSet.add(c);
        for (char c = 'a'; c <= 'z'; c++) tempSet.add(c);
        for (char c = '0'; c <= '9'; c++) tempSet.add(c);
        tempSet.addAll(Arrays.asList(' ', '-', '\'', '&', '!', '?', '.', ',', '_', '(', ')', '[', ']', '#', ':'));
        ENGLISH_COMMON_CHARS = Collections.unmodifiableSet(tempSet);
    }

    private MusicCharValidator() { throw new AssertionError("Utility class"); }

    private static boolean isChineseCommonChar(char c) { return c >= '一' && c <= '龥'; }
    private static boolean isEnglishCommonChar(char c) { return ENGLISH_COMMON_CHARS.contains(c); }
    private static boolean isRussianCommonChar(char c) { return (c >= 0x0410 && c <= 0x042F) || (c >= 0x0430 && c <= 0x044F); }
    private static boolean isJapaneseCommonChar(char c) { return (c >= 0x3041 && c <= 0x3096) || (c >= 0x30A1 && c <= 0x30F6) || (c >= 0xFF66 && c <= 0xFF9F); }
    private static boolean isFrenchCommonChar(char c) { return isEnglishCommonChar(c) || FRENCH_SPECIFIC_UNICODES.contains((int) c); }
    private static boolean isGermanCommonChar(char c) { return isEnglishCommonChar(c) || GERMAN_SPECIFIC_UNICODES.contains((int) c); }
    private static boolean isKoreanCommonChar(char c) { return (c >= 0xAC00 && c <= 0xD7AF); }
    private static boolean isMusicSymbol(char c) { return MUSIC_SYMBOL_UNICODES.contains((int) c); }

    private static Set<Language> getCharLanguage(char c) {
        Set<Language> languages = EnumSet.noneOf(Language.class);
        if (isChineseCommonChar(c)) languages.add(Language.CHINESE);
        if (isEnglishCommonChar(c)) languages.add(Language.ENGLISH);
        if (isRussianCommonChar(c)) languages.add(Language.RUSSIAN);
        if (isJapaneseCommonChar(c)) languages.add(Language.JAPANESE);
        if (FRENCH_SPECIFIC_UNICODES.contains((int) c)) languages.add(Language.FRENCH);
        if (GERMAN_SPECIFIC_UNICODES.contains((int) c)) languages.add(Language.GERMAN);
        if (isKoreanCommonChar(c)) languages.add(Language.KOREAN);
        if (isMusicSymbol(c)) languages.add(Language.MUSIC_SYMBOL);
        return languages.isEmpty() ? EnumSet.of(Language.UNKNOWN) : languages;
    }

    private static boolean isSupportedChar(char c) {
        return isChineseCommonChar(c) || isEnglishCommonChar(c) || isRussianCommonChar(c)
                || isJapaneseCommonChar(c) || isFrenchCommonChar(c) || isGermanCommonChar(c)
                || isKoreanCommonChar(c) || isMusicSymbol(c);
    }

    public static boolean validateTitle(String title) {
        if (title == null || title.isEmpty()) return false;
        long unsupportedCount = title.chars().mapToObj(c -> (char) c).filter(c -> !isSupportedChar(c)).count();
        long validNonSymbolCount = title.chars().mapToObj(c -> (char) c).filter(c -> isSupportedChar(c) && !isMusicSymbol(c)).count();
        return (double) unsupportedCount / title.length() <= 0.5 && validNonSymbolCount >= 1;
    }

    public static Map<Language, Integer> getLanguagesWithCount(String title) {
        if (title == null || title.isEmpty()) return Collections.emptyMap();
        Map<Language, Integer> counted = title.chars()
                .mapToObj(c -> (char) c)
                .flatMap(c -> getCharLanguage(c).stream())
                .filter(lang -> lang != Language.UNKNOWN)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.summingInt(lang -> 1)));
        return Collections.unmodifiableMap(counted);
    }
}
