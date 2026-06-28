package com.gjl.music.watch;

import java.nio.file.Path;
import java.util.List;

/**
 * 文件变更检测事件 — 由 {@link FileChangeHandler} 实现（如 editor 中）发布，
 * 由 {@code WatchRuleService} 监听并提交管道。
 *
 * <p>位于 common 以便 playback 等其他模块也可以使用此事件模型。
 */
public record FileChangesDetectedEvent(Long taskId,
                                        List<Path> newFiles,
                                        List<Path> modFiles,
                                        List<Long> delSongIds) {

    public boolean hasProcessingFiles() {
        return (newFiles != null && !newFiles.isEmpty())
                || (modFiles != null && !modFiles.isEmpty());
    }
}
