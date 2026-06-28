package com.gjl.music.filesystem;

import com.gjl.music.exception.PipelineException;
import com.gjl.music.infra.util.AudioFileUtils;
import com.gjl.music.infra.util.PathUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件系统浏览服务实现 —— 从 editor 的 FileSystemModuleImpl 提取核心业务逻辑。
 */
@Component
public class FileSystemServiceImpl implements FileSystemService {

    /** 目录浏览缓存：保留当前目录 + 父目录 + 子目录（不递归）。
     *  消除 directory → directory-metadata 连续调用的冗余 listFiles()，
     *  以及进出子目录/返回上级的重复扫描。 */
    private final Map<String, BrowseResult> browseCache = new ConcurrentHashMap<>();

    @Override
    public BrowseResult browse(File root, String relativePath) {
        return doBrowse(root, relativePath != null ? relativePath : "");
    }

    @Override
    public File resolveFile(File root, String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return root;
        }
        File resolvedFile = new File(root, relativePath);
        validatePath(root, resolvedFile);
        return resolvedFile;
    }

    @Override
    public void validatePath(File root, File target) {
        // 先用 normalize 做轻量检查（避免昂贵的 getCanonicalPath 调用）
        try {
            String rootNorm = root.toPath().normalize().toAbsolutePath().toString();
            String targetNorm = target.toPath().normalize().toAbsolutePath().toString();
            if (targetNorm.startsWith(rootNorm)) return;
        } catch (Exception e) {
            // fall through to canonical check
        }
        // 兜底：canonical 检查（处理符号链接场景）
        try {
            String rootCanonical = root.getCanonicalPath();
            String targetCanonical = target.getCanonicalPath();
            if (!targetCanonical.startsWith(rootCanonical)
                    && !targetCanonical.equals(rootCanonical)) {
                throw new PipelineException("路径越出根目录范围");
            }
        } catch (SecurityException e) {
            throw e;
        } catch (IOException e) {
            throw new PipelineException("路径校验失败: " + e.getMessage());
        }
    }

    // ── 核心逻辑 ──

    BrowseResult doBrowse(File root, String relativePath) {
        // ── 缓存命中 ──
        BrowseResult cached = browseCache.get(relativePath);
        if (cached != null) {
            return cached;
        }

        // ── 实际目录扫描 ──
        File dir = resolveFile(root, relativePath);

        if (!dir.exists() || !dir.isDirectory()) {
            throw new PipelineException("目录不存在: " + relativePath);
        }

        List<String> folders = new ArrayList<>();
        List<FileInfo> files = new ArrayList<>();

        File[] children = dir.listFiles();
        if (children != null) {
            // 预缓存 isDirectory 和 absolutePath，避免排序/遍历时反复跨 VM stat
            record ChildEntry(File file, boolean isDir, String absPath) {}
            ChildEntry[] entries = new ChildEntry[children.length];
            String rootAbs = root.getAbsolutePath();
            for (int i = 0; i < children.length; i++) {
                entries[i] = new ChildEntry(children[i],
                        children[i].isDirectory(), children[i].getAbsolutePath());
            }
            Arrays.sort(entries, (a, b) -> {
                if (a.isDir && !b.isDir) return -1;
                if (!a.isDir && b.isDir) return 1;
                return a.file.getName().compareToIgnoreCase(b.file.getName());
            });

            for (ChildEntry entry : entries) {
                if (entry.isDir) {
                    folders.add(entry.file.getName());
                } else if (AudioFileUtils.isAudioFile(entry.file)) {
                    files.add(buildFileInfo(rootAbs, entry.file, entry.absPath));
                }
            }
        }

        String displayPath = relativePath.isEmpty() ? "/" : relativePath;
        BrowseResult result = new BrowseResult(displayPath, folders, files);

        // ── 更新缓存：保留 当前目录 + 父目录 + 子目录（不递归）──
        browseCache.put(relativePath, result);

        // 计算需要保留的路径集合
        Set<String> keep = new HashSet<>();
        keep.add(relativePath);                       // 当前目录
        String parent = parentPath(relativePath);
        if (parent != null) {
            keep.add(parent);                         // 父目录
        }
        for (String childDir : folders) {
            String childPath = relativePath.isEmpty() ? childDir
                    : relativePath + "/" + childDir;
            keep.add(childPath);                      // 子目录（占位，下次浏览时自动填充）
        }

        // 淘汰不在保留集合中的条目
        browseCache.keySet().removeIf(k -> !keep.contains(k));

        return result;
    }

    /** 取父目录路径："/a/b/c" → "/a/b"，"a/b" → "a"，"a" → ""，"" → null */
    private static String parentPath(String path) {
        if (path == null || path.isEmpty()) return null;
        int idx = path.lastIndexOf('/');
        if (idx < 0) return "";       // 单层目录 → 父为空（根）
        return path.substring(0, idx);
    }

    // ── 内部方法 ──

    FileInfo buildFileInfo(String rootAbs, File file, String absFile) {
        String fileName = file.getName();
        String name = stripExtension(fileName);
        String format = extension(fileName);

        // relPath：用缓存的绝对路径计算，避免额外 IO
        String relPath = absFile.substring(rootAbs.length());
        if (relPath.startsWith("/") || relPath.startsWith("\\")) {
            relPath = relPath.substring(1);
        }
        relPath = PathUtils.normalize(relPath);

        // 用 file.lastModified() 替代 Files.readAttributes()（省一次 stat 调用）
        String modifiedTime = java.time.Instant.ofEpochMilli(file.lastModified()).toString();

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
