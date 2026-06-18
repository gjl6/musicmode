package com.gjl.music.filesystem;

import com.gjl.music.exception.PipelineException;
import com.gjl.music.infra.util.AudioFileUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;


@Component
public class FileSystemServiceImpl implements FileSystemService {

    @Override
    public BrowseResult browse(File root, String relativePath) {
        return doBrowse(root, relativePath != null ? relativePath : "");
    }

    @Override
    public File resolveFile(File root, String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return root;
        }
        File resolved = new File(root, relativePath);
        validatePath(root, resolved);
        return resolved;
    }

    @Override
    public void validatePath(File root, File target) {
        try {
            String rootCanonical = root.getCanonicalPath();
            String targetCanonical = target.getCanonicalPath();

            if (!targetCanonical.startsWith(rootCanonical)) {
                if (!targetCanonical.equals(rootCanonical)) {
                    throw new PipelineException("路径越出根目录范围");
                }
            }
        } catch (SecurityException e) {
            throw e;
        } catch (IOException e) {
            throw new PipelineException("路径校验失败: " + e.getMessage());
        }
    }


    BrowseResult doBrowse(File root, String relativePath) {
        File dir = resolveFile(root, relativePath);

        if (!dir.exists() || !dir.isDirectory()) {
            throw new PipelineException("目录不存在: " + relativePath);
        }

        List<String> folders = new ArrayList<>();
        List<FileInfo> files = new ArrayList<>();

        File[] children = dir.listFiles();
        if (children != null) {
            Arrays.sort(children, (a, b) -> {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });

            for (File child : children) {
                if (child.isDirectory()) {
                    folders.add(child.getName());
                } else if (AudioFileUtils.isAudioFile(child)) {
                    files.add(buildFileInfo(root, child));
                }
            }
        }

        String displayPath = relativePath.isEmpty() ? "/" : relativePath;
        return new BrowseResult(displayPath, folders, files);
    }


    FileInfo buildFileInfo(File root, File file) {
        String absRoot = root.getAbsolutePath();
        String absFile = file.getAbsolutePath();

        String relPath = absFile.substring(absRoot.length()).replace('\\', '/');
        if (relPath.startsWith("/")) {
            relPath = relPath.substring(1);
        }

        String fileName = file.getName();
        String name = stripExtension(fileName);
        String format = extension(fileName);

        String modifiedTime = "";
        try {
            Path p = file.toPath();
            BasicFileAttributes attr = Files.readAttributes(p, BasicFileAttributes.class);
            modifiedTime = attr.lastModifiedTime().toString();
        } catch (Exception ignored) {
        }

        return new FileInfo(name, fileName, format, relPath, file.length(), modifiedTime);
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot + 1).toLowerCase() : "";
    }
}
