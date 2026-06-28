package com.gjl.music.watch;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * {@link FileChangeHandler} 的 editor 实现 — 将扫描结果发布为 {@link FileChangesDetectedEvent}，
 * 由 {@code WatchRuleService} 监听并提交管道。
 */
@Component
public class WatchRuleChangeHandler implements FileChangeHandler {

    private final ApplicationEventPublisher eventPublisher;

    public WatchRuleChangeHandler(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void onChanges(TaskInfo task, ScanResult result) {
        eventPublisher.publishEvent(new FileChangesDetectedEvent(
                task.getId(),
                result.newFiles(),
                result.modFiles(),
                result.delSongIds()));
    }
}
