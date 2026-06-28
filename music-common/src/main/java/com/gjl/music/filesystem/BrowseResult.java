package com.gjl.music.filesystem;

import java.util.List;

/** 目录浏览结果 */
public record BrowseResult(
        String path,
        List<String> folders,
        List<FileInfo> files) {
}
