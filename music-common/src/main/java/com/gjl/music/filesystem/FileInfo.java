package com.gjl.music.filesystem;


public record FileInfo(
        String name,
        String fileName,
        String format,
        String path,
        long size,
        String modifiedTime) {
}
