package com.gjl.music.watch;

import java.nio.file.Path;
import java.util.List;

/**
 * 单次扫描的结果 — 由 {@link AutoTaskRunner} 返回。
 */
public record ScanResult(List<Path> newFiles, List<Path> modFiles,
                          List<Long> delSongIds, List<Path> delPaths) {

    public boolean isEmpty() {
        return (newFiles == null || newFiles.isEmpty())
                && (modFiles == null || modFiles.isEmpty())
                && (delSongIds == null || delSongIds.isEmpty());
    }
}
