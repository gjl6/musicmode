package com.gjl.music.module.song.dedup;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** 一组重复文件的描述 */
@Getter
@AllArgsConstructor
public class DuplicateGroup {

    public enum DuplicateType {
        /** 基于 SHA-256 全文件哈希的精确重复 */
        FILE_HASH,
        /** 基于文件名的重复 */
        FILE_NAME,
        /** 基于元数据（标题+艺术家+时长+专辑）的重复 */
        METADATA,
        /** 基于 Chromaprint 声学指纹相似度的重复 */
        FINGERPRINT
    }

    private final DuplicateType type;
    private final List<String> filePaths;
    private final List<String> fileNames;

    public int size() {
        return filePaths.size();
    }

    @Override
    public String toString() {
        return "DuplicateGroup[" + type + " x" + filePaths.size() + "]";
    }
}
