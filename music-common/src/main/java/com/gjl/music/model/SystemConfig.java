package com.gjl.music.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 系统配置模型 —— 统一管理所有可配置项，支持热更新 + 前端管理 */
@Getter
@Setter
public class SystemConfig {
    private Long id;
    private String configKey;
    private String configValue;
    private String category;
    private String label;
    private String description;
    private String valueType;          // STRING/INT/LONG/DOUBLE/BOOLEAN/LIST
    private boolean isSensitive;
    private boolean isRestartRequired;
    private int sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
