package com.gjl.music.config;

/**
 * 配置变更事件 —— 当配置被修改时发布，各模块可监听自己关心的 key 进行热更新。
 *
 * @param configKey   配置键
 * @param oldValue    旧值（首次插入时为 null）
 * @param newValue    新值
 */
public record ConfigChangedEvent(String configKey, String oldValue, String newValue) {

    public int asInt() {
        return Integer.parseInt(newValue);
    }

    public int asInt(int fallback) {
        try { return Integer.parseInt(newValue); }
        catch (NumberFormatException e) { return fallback; }
    }

    public long asLong() {
        return Long.parseLong(newValue);
    }

    public long asLong(long fallback) {
        try { return Long.parseLong(newValue); }
        catch (NumberFormatException e) { return fallback; }
    }

    public double asDouble() {
        return Double.parseDouble(newValue);
    }

    public double asDouble(double fallback) {
        try { return Double.parseDouble(newValue); }
        catch (NumberFormatException e) { return fallback; }
    }

    public boolean asBoolean() {
        return Boolean.parseBoolean(newValue);
    }
}
