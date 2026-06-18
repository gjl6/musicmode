package com.gjl.music.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;


@Getter
@Setter
public class SystemConfig {
    private Long id;
    private String configKey;
    private String configValue;
    private String category;
    private String label;
    private String description;
    private String valueType;
    private boolean isSensitive;
    private boolean isRestartRequired;
    private int sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
