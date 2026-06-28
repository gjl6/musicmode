package com.gjl.music.module.song.organize;

import com.gjl.music.model.MusicMetadata;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 整理路径构建工具 —— 根据元数据层级字段生成目标文件路径。
 *
 * <p>职责：
 * <ul>
 *   <li>根据层级配置和元数据逐级拼接目标目录</li>
 *   <li>清理文件名/路径中的非法字符（\ / : * ? " &lt; &gt; |）</li>
 *   <li>处理缺失元数据（使用 "Unknown" 占位）</li>
 *   <li>处理目标文件同名冲突（自动递增编号）</li>
 * </ul>
 */
@Slf4j
public final class OrganizePathBuilder {

    /** 文件系统非法字符的正则 */
    private static final String INVALID_CHARS = "[\\\\/:*?\"<>|]";

    /** 路径段最大长度（热更新） */
    static volatile int maxSegmentLength = 200;

    /** 冲突编号最大尝试次数（热更新） */
    static volatile int maxConflictRetries = 1000;

    private OrganizePathBuilder() { /* utility */ }

    /**
     * 根据层级配置构建目标文件路径。
     *
     * @param sourceFile 源文件路径
     * @param levels     有序的层级字段列表
     * @param targetRoot 目标根目录（为 null 时使用源文件父目录）
     * @param meta       文件的元数据
     * @return 最终目标文件路径
     */
    public static Path buildTargetPath(Path sourceFile,
                                        List<FileOrganizeModule.OrganizeField> levels,
                                        Path targetRoot,
                                        MusicMetadata meta) {
        // 1. 确定目标根目录
        Path effectiveRoot = (targetRoot != null) ? targetRoot : sourceFile.getParent();
        if (effectiveRoot == null) effectiveRoot = Path.of(".");

        // 2. 逐级拼接目录
        Path currentDir = effectiveRoot;
        for (FileOrganizeModule.OrganizeField level : levels) {
            String rawValue = level.resolve(meta);
            String segment = sanitizePathSegment(rawValue);
            currentDir = currentDir.resolve(segment);
        }

        // 3. 构建目标文件名（保留原始文件名）
        String sourceFileName = sourceFile.getFileName().toString();
        Path targetPath = currentDir.resolve(sourceFileName);

        // 4. 同名冲突处理
        targetPath = resolveConflict(targetPath);

        return targetPath;
    }

    /**
     * 清理路径段，替换非法字符，处理空值。
     */
    public static String sanitizePathSegment(String value) {
        if (value == null || value.isBlank()) return FileOrganizeModule.UNKNOWN;

        String cleaned = value.strip()
                .replaceAll(INVALID_CHARS, "_")       // 替换文件系统非法字符
                .replaceAll("\\.+$", "")               // 去掉尾部点号（Windows 限制）
                .replaceAll("\\s+$", "")               // 去掉尾部空格
                .strip();

        if (cleaned.isEmpty()) return FileOrganizeModule.UNKNOWN;

        // 限制段长度
        if (cleaned.length() > maxSegmentLength) {
            cleaned = cleaned.substring(0, maxSegmentLength).strip();
        }

        return cleaned;
    }

    /**
     * 处理目标文件同名冲突，通过添加递增编号解决。
     * 例如：track.mp3 → track (1).mp3 → track (2).mp3 ...
     */
    static Path resolveConflict(Path targetPath) {
        if (!Files.exists(targetPath)) return targetPath;

        String fileName = targetPath.getFileName().toString();
        Path parent = targetPath.getParent();
        if (parent == null) parent = Path.of(".");

        int dotIdx = fileName.lastIndexOf('.');
        String baseName = dotIdx > 0 ? fileName.substring(0, dotIdx) : fileName;
        String ext = dotIdx > 0 ? fileName.substring(dotIdx) : "";

        for (int i = 1; i <= maxConflictRetries; i++) {
            String newName = baseName + " (" + i + ")" + ext;
            Path candidate = parent.resolve(newName);
            if (!Files.exists(candidate)) {
                log.debug("冲突解决: {} → {}", fileName, newName);
                return candidate;
            }
        }

        // 超出最大尝试次数
        throw new IllegalStateException(
                "无法解决文件名冲突，已超过最大尝试次数 " + maxConflictRetries
                + ": " + targetPath);
    }
}
