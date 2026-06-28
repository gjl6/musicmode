package com.gjl.music.dto;

import java.util.Map;

/**
 * 工具请求体 —— 前端调用 /api/tools/* 的统一请求格式。
 *
 * <p>options 包含：path（浏览目录）、files（选中文件列表）及各模块专用配置。</p>
 */
public class ToolRequest {

    private Map<String, Object> options;

    public Map<String, Object> getOptions() { return options; }
    public void setOptions(Map<String, Object> options) { this.options = options; }
}
