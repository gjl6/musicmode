package com.gjl.music.playback.model;

/**
 * HTTP Range 解析结果。
 */
public class Range {
    private final long start;
    private final long end;        // inclusive
    private final boolean suffix;  // 后缀请求时，请求的 suffix 可能 > fileSize
    private final boolean unsatisfiable;

    public Range(long start, long end, boolean suffix) {
        this.start = start;
        this.end = end;
        this.suffix = suffix;
        this.unsatisfiable = false;
    }

    private Range(boolean unsatisfiable) {
        this.start = 0;
        this.end = 0;
        this.suffix = false;
        this.unsatisfiable = true;
    }

    public static Range unsatisfiable() {
        return new Range(true);
    }

    public long getStart() { return start; }

    /** @return 结束位置（含），-1 表示不可满足 */
    public long getEnd() { return unsatisfiable ? -1 : end; }

    /** @return 请求的字节数 */
    public long getLength() { return unsatisfiable ? 0 : end - start + 1; }

    public boolean isUnsatisfiable() { return unsatisfiable; }

    /** 构建 Content-Range 响应头值 */
    public String toContentRangeHeader(long fileSize) {
        if (unsatisfiable) return "bytes */" + fileSize;
        return "bytes " + start + "-" + end + "/" + fileSize;
    }

    @Override
    public String toString() {
        if (unsatisfiable) return "Range{unsatisfiable}";
        return "Range{" + start + "-" + end + ", length=" + getLength() + "}";
    }
}
