package com.gjl.music.model;

public enum LyricType {
    NONE("无歌词"),
    METADATA("元数据内部"),
    LRC("LRC格式");

    private final String description;

    LyricType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
