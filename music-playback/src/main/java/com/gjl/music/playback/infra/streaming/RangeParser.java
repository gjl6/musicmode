package com.gjl.music.playback.infra.streaming;

import com.gjl.music.playback.model.Range;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class RangeParser {

    private RangeParser() {}


    private static final Pattern RANGE_PATTERN =
            Pattern.compile("bytes=(\\d*)-(\\d*)\\s*");


    public static Range parse(String rangeHeader, long fileSize) {
        if (rangeHeader == null || rangeHeader.isBlank()) {
            return null;
        }

        Matcher m = RANGE_PATTERN.matcher(rangeHeader);
        if (!m.find()) {
            return null;
        }

                String after = rangeHeader.substring(m.end());
        if (after.contains(",") && RANGE_PATTERN.matcher(after).find()) {
                        return null;
        }

        String startStr = m.group(1);
        String endStr = m.group(2);

        try {
            if (!startStr.isEmpty() && !endStr.isEmpty()) {
                                long start = Long.parseLong(startStr);
                long end = Long.parseLong(endStr);
                if (start > end || start >= fileSize) {
                    return Range.unsatisfiable();
                }
                return new Range(start, Math.min(end, fileSize - 1), false);
            } else if (!startStr.isEmpty()) {
                                long start = Long.parseLong(startStr);
                if (start >= fileSize) {
                    return Range.unsatisfiable();
                }
                return new Range(start, fileSize - 1, false);
            } else if (!endStr.isEmpty()) {
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
