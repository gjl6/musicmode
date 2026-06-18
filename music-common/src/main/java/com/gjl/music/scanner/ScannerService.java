package com.gjl.music.scanner;

import java.nio.file.Path;
import java.util.List;


public interface ScannerService {


    List<Path> scan(Path... roots);


    boolean isAudioFile(Path path);


    int estimateFileCount(Path dir) throws java.io.IOException;
}
