package com.gjl.music.playback.config;

import com.gjl.music.mapper.WatchRuleMapper;
import com.gjl.music.model.WatchRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 默认任务初始化器 — 启动时检查并创建 playback 所需的默认 WatchRule。
 *
 * <p>在 {@link com.gjl.music.watch.AutoTaskScheduler} 之前执行，
 * 确保调度器启动时默认任务已在 DB 中。
 */
@Slf4j
@Component
@Order(0)
public class DefaultTasksInitializer implements ApplicationRunner {

    private static final String DEFAULT_TASK_NAME = "音乐库同步";

    private final WatchRuleMapper mapper;

    public DefaultTasksInitializer(WatchRuleMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 检查是否已存在
        java.util.List<WatchRule> existing = mapper.selectAll();
        boolean exists = existing != null && existing.stream()
                .anyMatch(r -> DEFAULT_TASK_NAME.equals(r.getName()));

        if (exists) {
            log.info("默认任务 '{}' 已存在，跳过创建", DEFAULT_TASK_NAME);
            return;
        }

        WatchRule task = new WatchRule();
        task.setName(DEFAULT_TASK_NAME);
        task.setWatchPath("import");
        task.setEnabled(true);
        task.setDirScanEnabled(true);
        task.setDirScanIntervalSec(300);  // 5 分钟
        task.setFileScanEnabled(false);
        task.setFileScanIntervalSec(3600);
        task.setStepsJson("[{\"name\":\"import-to-db\"}]");
        task.setPriority(0);
        task.setDescription("默认创建 — 扫描音乐目录，新文件自动入库，已删除文件自动清理");

        mapper.insert(task);
        log.info("默认任务 '{}' 已创建: id={}, dirScan=300s, step=import-to-db",
                DEFAULT_TASK_NAME, task.getId());
    }
}
