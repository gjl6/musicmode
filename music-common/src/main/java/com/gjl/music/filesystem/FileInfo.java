package com.gjl.music.filesystem;

/** 文件基本信息（文件系统级别，不含元数据解析结果） */
public record FileInfo(
        String name,
        String fileName,
        String format,
        String path,
        long size,
        String modifiedTime) {
}
