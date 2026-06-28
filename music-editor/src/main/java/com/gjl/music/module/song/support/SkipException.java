package com.gjl.music.module.song.support;

/**
 * 表示当前项无需处理（正常跳过），携带原因说明。
 * 由 GapFillingModule 捕获后记录 SKIPPED 状态及原因到 item log。
 */
public class SkipException extends RuntimeException {
    public SkipException(String reason) { super(reason); }
}
