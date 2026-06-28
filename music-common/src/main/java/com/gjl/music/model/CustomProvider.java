package com.gjl.music.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 自定义标签源模型 —— 用户可从前端添加自定义音乐搜索来源 */
@Getter
@Setter
public class CustomProvider {
    private Long id;
    private String name;           // 唯一标识
    private String label;          // 显示名
    private String icon;           // emoji 图标
    private String description;
    private boolean enabled;
    private String mode;           // BRIDGE / JAVA
    private String configJson;     // 配置 JSON
    private String sourceCode;     // JAVA 模式源码
    private String metadataTags;   // 逗号分隔的标签
    private String version;
    private int sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
