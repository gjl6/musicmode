package com.gjl.music.watch;

import java.nio.file.Path;
import java.util.List;


@FunctionalInterface
public interface FileWatchHandler {


    void onNewFiles(List<Path> batch);


    default void onModifiedFiles(List<Path> batch) {
        onNewFiles(batch);
    }


    default void onDeletedFiles(List<Long> songIds, List<Path> paths) {
            }
}
