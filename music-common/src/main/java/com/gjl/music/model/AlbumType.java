package com.gjl.music.model;

public enum AlbumType {
    ALBUM("录音室专辑"),
    EP("EP"),
    SINGLE("单曲"),
    COMPILATION("精选集"),
    SOUNDTRACK("原声带");

    private final String displayName;

    AlbumType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
