package com.gjl.music.filesystem;

import java.io.File;


public interface FileSystemService {


    BrowseResult browse(File root, String relativePath);


    File resolveFile(File root, String relativePath);


    void validatePath(File root, File target);
}
