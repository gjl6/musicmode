package com.gjl.music.filesystem;

import java.util.List;


public record BrowseResult(
        String path,
        List<String> folders,
        List<FileInfo> files) {
}
