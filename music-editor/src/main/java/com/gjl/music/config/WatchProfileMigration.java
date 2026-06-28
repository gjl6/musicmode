package com.gjl.music.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 自动任务 v2 迁移：为 watch_profile 表添加两级扫描 + 运行状态列，
 * 并根据 auto_trigger 值设置默认值。
 *
 * <p>支持 H2 和 MySQL 两种数据库的 ALTER TABLE 语法差异。
 * 通过检查目标列是否已存在（而非 marker key）判断是否需要迁移，
 * 避免前期语法错误导致 marker 误设。
 */
@Slf4j
@Component
public class WatchProfileMigration {

    private final JdbcTemplate jdbc;
    private final ConfigService configService;
    private final boolean isH2;

    public WatchProfileMigration(JdbcTemplate jdbc, ConfigService configService, DataSource dataSource) {
        this.jdbc = jdbc;
        this.configService = configService;
        this.isH2 = detectH2(dataSource);
    }

    private static boolean detectH2(DataSource ds) {
        try (Connection c = ds.getConnection()) {
            String name = c.getMetaData().getDatabaseProductName();
            return name != null && name.toLowerCase().contains("h2");
        } catch (Exception e) {
            log.warn("WatchProfileMigration: 无法检测数据库类型, 默认按 MySQL 处理");
            return false;
        }
    }

    @PostConstruct
    public void migrate() {
        // 先检查目标列是否已存在（比 marker key 更可靠）
        if (columnExists("dir_scan_enabled")) {
            log.debug("WatchProfileMigration: 列已存在, 跳过迁移");
            return;
        }

        int columnsAdded = 0;
        try {
            columnsAdded += addColumnIfNotExists("dir_scan_enabled",
                    boolType(), "1", "启用目录级扫描");
            columnsAdded += addColumnIfNotExists("dir_scan_interval_sec",
                    "INT", "60", "目录扫描间隔(秒)");
            columnsAdded += addColumnIfNotExists("file_scan_enabled",
                    boolType(), "1", "启用文件级扫描");
            columnsAdded += addColumnIfNotExists("file_scan_interval_sec",
                    "INT", "3600", "文件扫描间隔(秒)");
            columnsAdded += addColumnIfNotExists("state",
                    "VARCHAR(16)", "'IDLE'", "运行状态");
            columnsAdded += addColumnIfNotExists("last_scan_at",
                    "TIMESTAMP", null, "上次扫描时间");
            columnsAdded += addColumnIfNotExists("last_run_at",
                    "TIMESTAMP", null, "上次管道提交时间");
            columnsAdded += addColumnIfNotExists("error_message",
                    "VARCHAR(1024)", null, "最后一次错误信息");
        } catch (Exception e) {
            log.warn("WatchProfileMigration: 加列失败: {}", e.getMessage());
        }

        // 存量数据：auto_trigger=true → 两级均启用
        try {
            int updated = jdbc.update(
                    "UPDATE watch_profile SET dir_scan_enabled = auto_trigger, " +
                    "file_scan_enabled = auto_trigger WHERE auto_trigger = TRUE");
            int disabledUpdated = jdbc.update(
                    "UPDATE watch_profile SET dir_scan_enabled = FALSE, " +
                    "file_scan_enabled = FALSE WHERE auto_trigger = FALSE");
            updated += disabledUpdated;
            if (updated > 0) {
                log.info("WatchProfileMigration: {} 条记录已设置扫描默认值", updated);
            }
        } catch (Exception e) {
            log.debug("WatchProfileMigration: 更新默认值跳过: {}", e.getMessage());
        }

        configService.updateValue("watch.migration.v2_task_model", "true");
        log.info("WatchProfileMigration: 完成, 新增 {} 列 (isH2={})", columnsAdded, isH2);
    }

    /** H2 用 BOOLEAN，MySQL 用 TINYINT(1) */
    private String boolType() { return isH2 ? "BOOLEAN" : "TINYINT(1)"; }

    /**
     * 仅当列不存在时执行 ALTER TABLE ADD COLUMN。
     * H2: ALTER TABLE t ADD COLUMN c type DEFAULT x  (不支持 COMMENT 子句)
     * MySQL: ALTER TABLE t ADD COLUMN c type DEFAULT x COMMENT '...'
     */
    private int addColumnIfNotExists(String colName, String colType,
                                      String defaultVal, String comment) {
        try {
            if (columnExists(colName)) {
                log.debug("WatchProfileMigration: 列已存在 watch_profile.{}", colName);
                return 0;
            }

            StringBuilder sql = new StringBuilder("ALTER TABLE watch_profile ADD COLUMN ")
                    .append(colName).append(' ').append(colType);
            if (defaultVal != null) {
                sql.append(" DEFAULT ").append(defaultVal);
            }
            if (!isH2 && comment != null) {
                sql.append(" COMMENT '").append(comment).append('\'');
            }

            jdbc.execute(sql.toString());
            log.info("WatchProfileMigration: 新增列 watch_profile.{}", colName);
            return 1;
        } catch (Exception e) {
            log.warn("WatchProfileMigration: 加列 {} 失败: {}", colName, e.getMessage());
            return 0;
        }
    }

    /** 检查 watch_profile 表中指定列是否已存在 */
    private boolean columnExists(String colName) {
        try {
            String sql = isH2
                    ? "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                      "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = 'WATCH_PROFILE' AND COLUMN_NAME = ?"
                    : "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                      "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'watch_profile' AND COLUMN_NAME = ?";
            Integer count = jdbc.queryForObject(sql, Integer.class, colName.toUpperCase());
            return count != null && count > 0;
        } catch (Exception e) {
            log.debug("WatchProfileMigration: 检查列 {} 失败: {}", colName, e.getMessage());
            return false;
        }
    }
}
