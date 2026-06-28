package com.gjl.music.playback.infra.streaming;

import com.gjl.music.playback.model.Range;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP Range 请求头解析器。
 *
 * <p>支持格式：
 * <ul>
 *   <li>{@code bytes=0-1023} — 前 1KB</li>
 *   <li>{@code bytes=1024-} — 从 offset 到末尾</li>
 *   <li>{@code bytes=-2048} — 后缀（最后 N 字节）</li>
 *   <li>{@code bytes=0-1023, 2048-4095} — 多范围（暂不支持，降级 200 OK）</li>
 * </ul>
 */
public final class RangeParser {

    private RangeParser() {}

    /** bytes=start-end 或 bytes=start- 或 bytes=-suffix */
    private static final Pattern RANGE_PATTERN =
            Pattern.compile("bytes=(\\d*)-(\\d*)\\s*");

    /**
     * 解析 Range 请求头。
     *
     * @param rangeHeader Range 请求头值（可能为 {@code null}）
     * @param fileSize    目标文件总大小（字节）
     * @return 解析后的 {@link Range} 对象；无 Range 头或解析失败返回 {@code null}
     */
    public static Range parse(String rangeHeader, long fileSize) {
        if (rangeHeader == null || rangeHeader.isBlank()) {
            return null;
        }

        Matcher m = RANGE_PATTERN.matcher(rangeHeader);
        if (!m.find()) {
            return null;
        }

        // 检测多范围（逗号后有第二个 range）
        String after = rangeHeader.substring(m.end());
        if (after.contains(",") && RANGE_PATTERN.matcher(after).find()) {
            // 多范围请求，暂不支持，返回 null（降级 200）
            return null;
        }

        String startStr = m.group(1);
        String endStr = m.group(2);

        try {
            if (!startStr.isEmpty() && !endStr.isEmpty()) {
                // bytes=start-end
                long start = Long.parseLong(startStr);
                long end = Long.parseLong(endStr);
                if (start > end || start >= fileSize) {
                    return Range.unsatisfiable();
                }
                return new Range(start, Math.min(end, fileSize - 1), false);
            } else if (!startStr.isEmpty()) {
                // bytes=start-
                long start = Long.parseLong(startStr);
                if (start >= fileSize) {
                    return Range.unsatisfiable();
                }
                return new Range(start, fileSize - 1, false);
            } else if (!endStr.isEmpty()) {
                // bytes=-suffix (最后 N 字节)
                long suffix = Long.parseLong(endStr);
                if (suffix == 0) {
                    return Range.unsatisfiable();
                }
                long start = Math.max(0, fileSize - suffix);
                return new Range(start, fileSize - 1, suffix > fileSize);
            }
        } catch (NumberFormatException e) {
            return null;
        }

        return null;
    }

}
