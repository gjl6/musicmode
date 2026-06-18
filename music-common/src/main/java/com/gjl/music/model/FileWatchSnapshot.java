package com.gjl.music.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;


@Getter
@Setter
public class FileWatchSnapshot {
    private String dirPath;
    private long lastModified;
    private int fileCount;
    private LocalDateTime updatedAt;
}
